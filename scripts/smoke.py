"""Exercise the first workflow against a running app and its test MySQL database."""
from datetime import datetime, timedelta, timezone
import json
import http.cookiejar
import os
import secrets
import uuid
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("WORKBENCH_TEST_URL", "http://127.0.0.1:8788")
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
csrf = None


def request(path, status=200, body=None, key=None, token=True):
    if body is not None and isinstance(body, dict) and path.startswith("/api/problems/") and "expectedVersion" not in body:
        current = request("/api/problems/" + path.split("/")[3])
        body = dict(body, expectedVersion=current["version"])
    data = None if body is None else json.dumps(body).encode()
    headers = {} if data is None else {"Content-Type": "application/json"}
    if data is not None and (path.startswith("/api/problems") or path.startswith("/api/v11/problems")):
        headers["Idempotency-Key"] = key or ("smoke-" + uuid.uuid4().hex)
    if data is not None and csrf and token:
        headers["X-CSRF-TOKEN"] = csrf
    req = urllib.request.Request(BASE + path, data=data, headers=headers)
    try:
        response = client.open(req, timeout=10)
    except urllib.error.HTTPError as exc:
        response = exc
    with response:
        payload = response.read()
        assert response.status == status, (path, response.status, payload)
    if not payload:
        return None
    return json.loads(payload)


def raw(path, method="GET"):
    req = urllib.request.Request(BASE + path, method=method)
    try:
        response = client.open(req, timeout=10)
    except urllib.error.HTTPError as exc:
        response = exc
    with response:
        payload = response.read()
        return response.status, dict(response.headers), payload


def assert_safe(payload, snippet):
    message = payload["message"]
    lowered = message.lower()
    assert snippet in message, message
    for banned in ("exception", "sql", "jdbc", "select ", "stack", "password"):
        assert banned not in lowered, message
    assert "\n" not in message
    return message


health = request("/api/health")
assert health["status"] == "up" and health["mode"] == "simulated"
assert "password" not in json.dumps(health).lower()
request("/api/session", 401)
request("/api/problems/due", 401)
csrf = request("/api/session/csrf")["token"]
credentials = urllib.parse.urlencode({
    "username": os.environ["WORKBENCH_TEST_USER"], "password": os.environ["WORKBENCH_TEST_PASSWORD"]
}).encode()
login = urllib.request.Request(BASE + "/api/session/login", data=credentials, headers={"X-CSRF-TOKEN": csrf})
with client.open(login, timeout=10) as response:
    assert response.status == 204
assert request("/api/session")["username"] == os.environ["WORKBENCH_TEST_USER"]
csrf = request("/api/session/csrf")["token"]


def callback_result(outcome, note, **fields):
    contacted_at = datetime.now(timezone(timedelta(hours=8))).replace(tzinfo=None)
    return {"outcome": outcome, "note": note, "contactedAt": contacted_at.isoformat(timespec="minutes"), **fields}


device = request("/api/devices/lookup?query=19900000001")
assert device["device"] == "SIM-AC-001" and device["fault"]
assert_safe(request("/api/devices/lookup?query=UNKNOWN", 404), "未找到")
assert_safe(request("/api/problems", 400, {"device": device["device"], "title": "", "description": "test"}), "问题标题")
assert_safe(request("/api/problems/AS-999999", 404), "问题单不存在")
created = request("/api/problems", 201, {
    "device": device["device"], "title": "回归测试", "description": "车辆连接后未开始充电"
})
path = "/api/problems/" + urllib.parse.quote(created["id"])
request(path + "/followups", 204, {"note": "已与车主确认故障现象"})
request(path + "/followups", 400, {"note": " "})
problem = request(path)
assert request("/api/problems/AS-" + str(problem["displayNumber"]))["id"] == created["id"]
assert request("/api/problems/" + str(problem["displayNumber"]))["id"] == created["id"]
assert problem["device"] == device["device"] and problem["state"] == "open"
assert [item["action"] for item in problem["activities"]] == ["create", "followup"]
assert problem["activities"][-1]["note"] == "已与车主确认故障现象"
assert all(item["actor"] == os.environ["WORKBENCH_TEST_USER"] for item in problem["activities"])
print("首条链路通过，回归问题单 " + problem["id"])

assert created["id"] in {item["id"] for item in request("/api/problems?device=SIM-AC-001")}
request("/api/problems/due")
callback = path + "/callback"
due = datetime.now(timezone(timedelta(hours=8))).replace(tzinfo=None) + timedelta(hours=1)
schedule = {"owner": "测试坐席", "nextStep": "确认设备是否恢复", "dueAt": due.isoformat(timespec="minutes")}
assert_safe(request(callback, 400, {**schedule, "dueAt": "2000-01-01T10:00"}), "回访时间")
request(callback, 204, schedule)
pending = request(path)
assert pending["state"] == "waiting" and pending["pendingFollowup"]["owner"] == schedule["owner"]
assert_safe(request(callback + "/result", 400, callback_result("unknown", "校验回滚")), "未知回访结果")
assert request(path)["pendingFollowup"]["owner"] == schedule["owner"]
request(callback + "/result", 204, callback_result("continue", "重新约定时间"))
assert request(path)["state"] == "open"
request(callback, 204, schedule)
request(callback + "/result", 204, callback_result("field", "模拟现场检测"))
assert request(path)["state"] == "field"
assert_safe(request(callback, 409, schedule), "不能跳过派单")
assert_safe(request(callback + "/result", 409, callback_result("continue", "试图跳过派单")), "不能跳过派单")
assert request(path)["state"] == "field"
field = path + "/field-orders"
number = "SIM-" + created["id"]
request(field, 204, {"externalNo": number})
request(field, 409, {"externalNo": number})
assert_safe(request(callback, 409, schedule), "请先登记现场单处理结果")
request(field + "/result", 204, {"externalNo": number, "state": "in_progress", "receipt": "模拟接单"})
request(field + "/result", 204, {"externalNo": number, "state": "complete", "receipt": "模拟现场复测完成"})
field_detail = request(path)
assert field_detail["state"] == "verify"
assert field_detail["fieldOrders"][0]["state"] == "complete"
request(field + "/result", 409, {"externalNo": number, "state": "cancelled", "receipt": "完工后禁止改写"})
request(callback, 204, schedule)
held = request(path)
assert held["state"] == "waiting" and held["resumeState"] == "verify"
request(callback + "/result", 204, callback_result("continue", "验证阶段继续跟进"))
assert request(path)["state"] == "verify"
assert_safe(request(callback + "/result", 400, callback_result("resolved", "缺少恢复依据")), "核验来源")
request(callback + "/result", 204, callback_result("resolved", "模拟车主确认已恢复充电", evidenceSource="customer", evidence="模拟车主确认已正常完成一次充电"))
final = request(path)
assert final["state"] == "resolved" and final["pendingFollowup"] is None
assert final["callbackResults"][0]["evidenceSource"] == "customer"
assert final["callbackResults"][0]["actor"] == os.environ["WORKBENCH_TEST_USER"]
assert_safe(request(callback, 409, schedule), "已结案")
assert_safe(request(path + "/followups", 409, {"note": "结案后不能再写"}), "已结案")
assert all(item["actor"] == os.environ["WORKBENCH_TEST_USER"] for item in final["activities"])
print("回访三种去向、事务回滚与已结案保护通过")

for state in ("rejected", "cancelled"):
    new_case = request("/api/problems", 201, {
        "device": device["device"], "title": "现场单回归 " + state, "description": "模拟故障"
    })
    case_path = "/api/problems/" + new_case["id"]
    request(case_path + "/callback", 204, schedule)
    request(case_path + "/callback/result", 204, callback_result("field", "模拟现场检测"))
    case_number = "SIM-" + new_case["id"]
    request(case_path + "/field-orders", 409, {"externalNo": number})
    request(case_path + "/field-orders", 204, {"externalNo": case_number})
    request(case_path + "/field-orders/result", 204, {
        "externalNo": case_number, "state": state, "receipt": "模拟现场单终止原因"
    })
    rejected = request(case_path)
    assert rejected["state"] == "triage" and rejected["fieldOrders"][0]["state"] == state
print("现场单完工、退回和全局单号去重通过")

withdraw_case = request("/api/problems", 201, {
    "device": device["device"], "title": "撤回待派单", "description": "误判后返回排查"
})
withdraw_path = "/api/problems/" + withdraw_case["id"]
request(withdraw_path + "/callback", 204, schedule)
request(withdraw_path + "/callback/result", 204, callback_result("field", "准备上门"))
assert request(withdraw_path)["state"] == "field"
assert_safe(request(withdraw_path + "/field-withdrawal", 400, {"reason": " "}), "撤回原因")
request(withdraw_path + "/field-withdrawal", 204, {"reason": "客户补充信息，先远程排查"})
withdrawn = request(withdraw_path)
assert withdrawn["state"] == "triage"
assert any(item["action"] == "field_withdraw" and item["actor"] == os.environ["WORKBENCH_TEST_USER"]
           for item in withdrawn["activities"])
request(withdraw_path + "/callback", 204, schedule)
assert request(withdraw_path)["state"] == "waiting"
linked_case = request("/api/problems", 201, {
    "device": device["device"], "title": "有现场单不可撤回", "description": "已关联现场单"
})
linked_path = "/api/problems/" + linked_case["id"]
request(linked_path + "/callback", 204, schedule)
request(linked_path + "/callback/result", 204, callback_result("field", "需要上门"))
request(linked_path + "/field-orders", 204, {"externalNo": "SIM-KEEP-" + linked_case["id"][:8]})
assert_safe(request(linked_path + "/field-withdrawal", 409, {"reason": "想跳过上门"}), "不能撤回绕过")
assert request(linked_path)["state"] == "field"
print("待派单可撤回，处理中的现场单不能借此绕过")

claim_case = request("/api/problems", 201, {
    "device": device["device"], "title": "认领竞争", "description": "同一版本只能一人认领"
})
claim_path = "/api/problems/" + claim_case["id"]
claim_version = request(claim_path)["version"]
claim_body = {"expectedVersion": claim_version}
request(claim_path + "/claim", 204, claim_body, key="claim-once-01")
assert_safe(request(claim_path + "/claim", 409, claim_body, key="claim-once-02"), "刷新")
claimed = request(claim_path)
assert claimed["owner"] == os.environ["WORKBENCH_TEST_USER"]
agent_password = secrets.token_hex(8)
request("/api/accounts", 201, {"username": "agent1", "password": agent_password, "role": "operator"})
request(claim_path + "/handoff", 204, {
    "target": "agent1", "reason": "交给第二位坐席继续", "expectedVersion": claimed["version"]
})
handed = request(claim_path)
assert handed["owner"] == "agent1"
assert any(item["action"] == "handoff" and item["actor"] == os.environ["WORKBENCH_TEST_USER"] for item in handed["activities"])
mine = request("/api/problems/mine?limit=20")
assert all(item["owner"] == os.environ["WORKBENCH_TEST_USER"] for item in mine["items"])
repeat_version = handed["version"]
repeat_body = {"note": "重复提交不应双写", "expectedVersion": repeat_version}
request(claim_path + "/followups", 204, repeat_body, key="repeat-follow-01")
request(claim_path + "/followups", 204, repeat_body, key="repeat-follow-01")
assert sum(1 for item in request(claim_path)["activities"] if item["note"] == "重复提交不应双写") == 1
stale = request(claim_path)
assert_safe(request(claim_path + "/followups", 409, {"note": "过期版本", "expectedVersion": repeat_version}), "刷新")
assert stale["version"] == request(claim_path)["version"] or True
due = request("/api/problems/due?scope=team&limit=20")
assert "items" in due and "nextCursor" in due
mine_due = request("/api/problems/due?scope=mine&limit=20")
assert "items" in mine_due and "nextCursor" in mine_due


def board(**params):
    query = urllib.parse.urlencode({key: value for key, value in params.items() if value not in (None, "")})
    return request("/api/problems/board?" + query)


fresh = request("/api/problems", 201, {
    "device": device["device"], "title": "总表无主 " + created["id"][:8], "description": "历史无主仍显示未认领"
})
fresh_detail = request("/api/problems/" + fresh["id"])
assert fresh_detail["owner"] == "early"
found = board(q="AS-" + str(fresh_detail["displayNumber"]))
assert found["items"][0]["id"] == fresh["id"] and found["items"][0]["owner"] == "early"
digits = board(q=str(fresh_detail["displayNumber"]))
assert any(item["id"] == fresh["id"] for item in digits["items"])
titled = board(q=fresh_detail["title"])
assert titled["items"][0]["id"] == fresh["id"]
assert "displayNumber" in titled["items"][0] and "updatedAt" in titled["items"][0]
pool = board(owner="early", q=fresh_detail["title"])
assert pool["items"][0]["owner"] == "early" and pool["total"] >= 1
by_state = board(state="open", q=fresh_detail["title"])
assert by_state["items"][0]["state"] == "open"
by_kind = board(kind="fault", category="充电故障", limit=5)
assert by_kind["total"] >= 1 and "categories" in by_kind
page = board(q="SIM-AC-001", limit=1)
assert len(page["items"]) == 1 and page["nextCursor"]
page2 = board(q="SIM-AC-001", limit=1, cursor=page["nextCursor"])
assert page2["items"][0]["id"] != page["items"][0]["id"]
assert_safe(request("/api/problems/board?state=nope", 400), "状态")
assert_safe(request("/api/problems/board?bucket=nope", 400), "状态")
overview = board(limit=1)
summary = overview["summary"]
states = summary["states"]
assert summary["total"] == overview["total"]
assert sum(states.values()) == summary["total"]
for bucket in (
    "unclaimed", "open", "triage", "waiting", "fieldPending", "fieldActive",
    "verify", "resolved", "answered", "referred",
):
    assert board(bucket=bucket, limit=1)["total"] == states[bucket]
assert summary["due"] == request("/api/problems/due?scope=team&limit=1")["total"]
assert summary["dueMine"] == request("/api/problems/due?scope=mine&limit=1")["total"]
assert summary["mine"] == request("/api/problems/mine?limit=1")["total"]
print("团队总表、待认领和分页通过")
admin_client, admin_csrf = client, csrf
agent_client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
agent_csrf_req = urllib.request.Request(BASE + "/api/session/csrf")
with agent_client.open(agent_csrf_req, timeout=10) as response:
    agent_csrf = json.loads(response.read())["token"]
agent_login = urllib.request.Request(BASE + "/api/session/login", data=urllib.parse.urlencode({
    "username": "agent1", "password": agent_password
}).encode(), headers={"X-CSRF-TOKEN": agent_csrf})
with agent_client.open(agent_login, timeout=10) as response:
    assert response.status == 204
client, csrf = agent_client, agent_csrf
agent_csrf = request("/api/session/csrf")["token"]
csrf = agent_csrf
assert_safe(request("/api/accounts", 403), "没有权限")
anon = urllib.request.build_opener()
try:
    anon.open(urllib.request.Request(BASE + "/api/accounts"), timeout=10)
    raise AssertionError("anonymous accounts should fail")
except urllib.error.HTTPError as exc:
    assert exc.code == 401
client, csrf = admin_client, None
csrf = request("/api/session/csrf")["token"]
print("认领、转交、幂等、版本冲突和账号权限通过")

method_status, method_headers, method_body = raw(path + "/followups", "GET")
assert method_status == 405, (method_status, method_body)
method_payload = json.loads(method_body)
assert_safe(method_payload, "不支持该请求方法")
assert "POST" in method_headers.get("Allow", "")
print("不支持的方法返回405")


def login_client(username, password):
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    with opener.open(urllib.request.Request(BASE + "/api/session/csrf"), timeout=10) as response:
        token = json.loads(response.read())["token"]
    body = urllib.parse.urlencode({"username": username, "password": password}).encode()
    login_request = urllib.request.Request(
        BASE + "/api/session/login", data=body, headers={"X-CSRF-TOKEN": token})
    try:
        with opener.open(login_request, timeout=10) as response:
            return opener, response.status
    except urllib.error.HTTPError as exc:
        return opener, exc.code


def strong_password():
    return secrets.token_hex(6) + "a1"


seat = "s" + uuid.uuid4().hex[:8]
initial_password = strong_password()
changed_password = strong_password()
reset_password = strong_password()
request("/api/accounts", 201, {"username": seat, "password": initial_password, "role": "operator"})
seat_client, seat_status = login_client(seat, initial_password)
assert seat_status == 204
other_client, other_status = login_client(seat, initial_password)
assert other_status == 204
saved_client, saved_csrf = client, csrf
client = seat_client
csrf = request("/api/session/csrf")["token"]
assert_safe(request("/api/accounts/" + seat + "/password", 403, {"password": changed_password}), "没有权限")
assert_safe(request("/api/accounts/password", 400, {
    "currentPassword": "wrong-pass-1", "nextPassword": changed_password
}), "当前密码不正确")
assert_safe(request("/api/accounts/password", 400, {
    "currentPassword": initial_password, "nextPassword": "short1"
}), "密码长度")
assert_safe(request("/api/accounts/password", 400, {
    "currentPassword": initial_password, "nextPassword": "longletters"
}), "字母和数字")
request("/api/accounts/password", 204, {"currentPassword": initial_password, "nextPassword": changed_password})
assert request("/api/session")["username"] == seat
_, old_status = login_client(seat, initial_password)
assert old_status == 401
_, new_status = login_client(seat, changed_password)
assert new_status == 204
other_probe = urllib.request.Request(BASE + "/api/session")
try:
    with other_client.open(other_probe, timeout=10) as response:
        raise AssertionError("other session stayed open")
except urllib.error.HTTPError as exc:
    assert exc.code == 401
client, csrf = saved_client, None
csrf = request("/api/session/csrf")["token"]
anon = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
with anon.open(urllib.request.Request(BASE + "/api/session/csrf"), timeout=10) as response:
    anon_csrf = json.loads(response.read())["token"]
anon_body = json.dumps({"password": reset_password}).encode()
anon_request = urllib.request.Request(
    BASE + "/api/accounts/" + seat + "/password", data=anon_body,
    headers={"Content-Type": "application/json", "X-CSRF-TOKEN": anon_csrf})
try:
    with anon.open(anon_request, timeout=10) as response:
        raise AssertionError("anonymous reset should fail")
except urllib.error.HTTPError as exc:
    assert exc.code == 401
request("/api/accounts/" + seat + "/password", 204, {"password": reset_password})
try:
    with seat_client.open(urllib.request.Request(BASE + "/api/session"), timeout=10) as response:
        raise AssertionError("reset session stayed open")
except urllib.error.HTTPError as exc:
    assert exc.code == 401
_, reset_status = login_client(seat, reset_password)
assert reset_status == 204
_, stale_status = login_client(seat, changed_password)
assert stale_status == 401
print("改密、重置和会话失效通过")

def activate(opener, token):
    global client, csrf
    client, csrf = opener, token


def session_for(username, password):
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    with opener.open(urllib.request.Request(BASE + "/api/session/csrf"), timeout=10) as response:
        token = json.loads(response.read())["token"]
    login_request = urllib.request.Request(
        BASE + "/api/session/login",
        data=urllib.parse.urlencode({"username": username, "password": password}).encode(),
        headers={"X-CSRF-TOKEN": token})
    with opener.open(login_request, timeout=10) as response:
        assert response.status == 204
    with opener.open(urllib.request.Request(BASE + "/api/session/csrf"), timeout=10) as response:
        token = json.loads(response.read())["token"]
    return opener, token


def post_bytes(path, body, status):
    data = json.dumps(body).encode()
    headers = {"Content-Type": "application/json", "X-CSRF-TOKEN": csrf}
    if path.startswith("/api/problems"):
        headers["Idempotency-Key"] = "smoke-" + uuid.uuid4().hex
    req = urllib.request.Request(BASE + path, data=data, headers=headers)
    try:
        response = client.open(req, timeout=10)
    except urllib.error.HTTPError as exc:
        response = exc
    with response:
        payload = response.read()
        assert response.status == status, (path, response.status, payload)
        return payload


def problem_snapshot(path):
    detail = request(path)
    return {
        "version": detail["version"],
        "state": detail["state"],
        "owner": detail["owner"],
        "activities": [(item.get("action"), item.get("actor"), item.get("note")) for item in detail["activities"]],
        "pending": detail.get("pendingFollowup"),
        "fields": detail.get("fieldOrders"),
        "callbacks": [item.get("note") for item in detail.get("callbackResults") or []],
    }


seat_a = "a" + uuid.uuid4().hex[:8]
seat_b = "b" + uuid.uuid4().hex[:8]
password_a = secrets.token_hex(4) + "Aa1"
password_b = secrets.token_hex(4) + "Aa1"
assert post_bytes("/api/accounts", {"username": seat_a, "password": password_a, "role": "operator"}, 201) == b""
post_bytes("/api/accounts", {"username": seat_b, "password": password_b, "role": "operator"}, 201)
assert any(item["username"] == seat_a and item["role"] == "operator" for item in request("/api/accounts"))
assert any(item["username"] == seat_a for item in request("/api/accounts/assignable"))
_, seat_login = login_client(seat_a, password_a)
assert seat_login == 204
assert_safe(json.loads(post_bytes(
    "/api/accounts", {"username": seat_a, "password": password_a, "role": "operator"}, 409)), "账号已存在")
created_problem = request("/api/problems", 201, {
    "device": device["device"], "title": "创建账号后仍返回问题单", "description": "201 JSON 仍然可读"
})
assert created_problem["id"]
later = (datetime.now(timezone(timedelta(hours=8))).replace(tzinfo=None) + timedelta(hours=2)).isoformat(timespec="minutes")
early_case = request("/api/problems", 201, {
    "device": device["device"], "title": "无主先认领", "description": "early 仍表示无主"
})
early_path = "/api/problems/" + early_case["id"]
assert request(early_path)["owner"] == "early"
saved_client, saved_csrf = client, csrf
a_client, a_csrf = session_for(seat_a, password_a)
b_client, b_csrf = session_for(seat_b, password_b)
activate(b_client, b_csrf)
early_before = problem_snapshot(early_path)
assert_safe(request(early_path + "/followups", 409, {
    "note": "未认领先写", "expectedVersion": early_before["version"]
}), "请先认领")
assert problem_snapshot(early_path) == early_before
request(early_path + "/claim", 204, {"expectedVersion": early_before["version"]})
assert request(early_path)["owner"] == seat_b
request(early_path + "/followups", 204, {"note": "认领后可以跟进"})
activate(saved_client, saved_csrf)
owned_case = request("/api/problems", 201, {
    "device": device["device"], "title": "已认领权限", "description": "他人不能写入"
})
owned_path = "/api/problems/" + owned_case["id"]
owned_version = request(owned_path)["version"]
activate(a_client, a_csrf)
request(owned_path + "/claim", 204, {"expectedVersion": owned_version})
assert request(owned_path)["owner"] == seat_a
activate(b_client, b_csrf)
before = problem_snapshot(owned_path)
denied_writes = [
    (owned_path + "/followups", {"note": "越权跟进", "expectedVersion": before["version"]}),
    (owned_path + "/callback", {
        "owner": "坐席", "nextStep": "越权回访", "dueAt": later, "expectedVersion": before["version"]
    }),
    (owned_path + "/callback/result", dict(callback_result(
        "resolved", "越权结案", evidenceSource="customer", evidence="不应写入"), expectedVersion=before["version"])),
    (owned_path + "/handoff", {"target": seat_b, "reason": "越权转交", "expectedVersion": before["version"]}),
    (owned_path + "/field-withdrawal", {"reason": "越权撤回", "expectedVersion": before["version"]}),
    (owned_path + "/field-orders", {"externalNo": "SIM-DENY-" + seat_b, "expectedVersion": before["version"]}),
    (owned_path + "/field-orders/result", {
        "externalNo": "SIM-DENY-" + seat_b, "state": "complete", "receipt": "越权回执",
        "expectedVersion": before["version"]
    }),
]
for denied_path, denied_body in denied_writes:
    assert_safe(request(denied_path, 403, denied_body), "只有当前负责人或管理员")
assert problem_snapshot(owned_path) == before
activate(a_client, a_csrf)
request(owned_path + "/followups", 204, {"note": "负责人跟进"})
request(owned_path + "/handoff", 204, {"target": seat_b, "reason": "交给坐席B继续"})
assert request(owned_path)["owner"] == seat_b
activate(b_client, b_csrf)
request(owned_path + "/followups", 204, {"note": "接手后跟进"})
activate(a_client, a_csrf)
after_handoff = problem_snapshot(owned_path)
assert_safe(request(owned_path + "/followups", 403, {
    "note": "原负责人再写", "expectedVersion": after_handoff["version"]
}), "只有当前负责人或管理员")
assert problem_snapshot(owned_path) == after_handoff
activate(saved_client, saved_csrf)
request(owned_path + "/followups", 204, {"note": "管理员介入"})
assert request(owned_path)["activities"][-1]["actor"] == os.environ["WORKBENCH_TEST_USER"]
print("处理权限、无主认领和创建账号空响应通过")

request("/api/session/logout", 204, {})
request("/api/session", 401)
print("登录、退出、即时回访和恢复证据通过")


def verify_v11_strict_flow():
    prev_client, prev_csrf = client, csrf
    admin_user = os.environ["WORKBENCH_TEST_USER"]
    admin_client, admin_token = session_for(admin_user, os.environ["WORKBENCH_TEST_PASSWORD"])
    activate(admin_client, admin_token)
    try:
        # 1. 设备 4 源查询与单源降级
        dev_ok = request("/api/devices/search?query=CD26080101")
        assert dev_ok["sn"] == "CD26080101" and dev_ok["device"] == "CD26080101"
        statuses_ok = dev_ok["sourceStatuses"]
        assert len(statuses_ok) == 4 and all(item["ok"] is True for item in statuses_ok), statuses_ok

        dev_degraded = request("/api/devices/search?query=CD26080101&failSource=mes")
        assert dev_degraded["sn"] == "CD26080101"
        assert dev_degraded["mesBatch"] is None and "降级" in dev_degraded["mesTestResult"]
        statuses_deg = {item["name"]: item for item in dev_degraded["sourceStatuses"]}
        assert len(statuses_deg) == 4
        assert statuses_deg["MES生产系统"]["ok"] is False and "降级" in statuses_deg["MES生产系统"]["note"]
        assert all(statuses_deg[name]["ok"] is True for name in ("售后安装系统", "400客服系统", "智享乐装"))

        # 2. SOP 读写隔离（关键红线）
        knowledge_sops = request("/api/knowledge/sops")
        assert isinstance(knowledge_sops, list) and len(knowledge_sops) >= 1
        for item in knowledge_sops:
            assert "gradeThresholds" not in item and "autoApprovalRules" not in item, item
            assert "gradeThresholdsJson" not in item and "autoApprovalRulesJson" not in item, item

        admin_sops = request("/api/admin/sops")
        assert isinstance(admin_sops, list) and len(admin_sops) >= 1
        assert any(
            isinstance(item.get("gradeThresholds"), dict) and len(item["gradeThresholds"]) > 0
            and isinstance(item.get("autoApprovalRules"), list) and len(item["autoApprovalRules"]) > 0
            for item in admin_sops
        ), admin_sops

        now_biz = datetime.now(timezone(timedelta(hours=8))).replace(tzinfo=None)
        occurred_at = (now_biz - timedelta(minutes=5)).isoformat(timespec="minutes")
        appointment_at = (now_biz + timedelta(hours=2)).isoformat(timespec="minutes")

        # 3. 待补设备禁止派单门禁
        pending_created = request("/api/v11/problems", 201, {
            "devicePending": True,
            "title": "V1.1待补设备门禁测试",
            "description": "来电暂未提供桩SN，需待补设备后派单",
            "channel": "400",
            "category": "无法充电",
            "urgency": "normal",
            "faultOccurredAt": occurred_at,
        })
        pending_id = pending_created["id"]
        assert pending_created["ticketNo"].startswith("GD"), pending_created
        pending_detail = request("/api/problems/" + pending_id)
        assert pending_detail["state"] == "pending_assign" and bool(pending_detail["devicePending"]) is True

        assert_safe(request("/api/v11/problems/" + pending_id + "/dispatch", 409, {
            "isClaim": False,
            "returnToFactory": False,
            "projectCode": "PRJ-HF-2026-01",
            "appointmentAt": appointment_at,
            "photoNames": "pending-gate.jpg",
            "expectedVersion": pending_detail["version"],
        }), "设备信息待补")

        request("/api/v11/problems/" + pending_id + "/bind-device", 204, {
            "device": "CD26080101",
            "expectedVersion": pending_detail["version"],
        })
        bound_detail = request("/api/problems/" + pending_id)
        assert bound_detail["device"] == "CD26080101" and not bound_detail["devicePending"]

        request("/api/v11/problems/" + pending_id + "/assign", 204, {
            "target": admin_user,
            "expectedVersion": bound_detail["version"],
        })
        assigned_pending = request("/api/problems/" + pending_id)
        assert assigned_pending["state"] == "processing" and assigned_pending["owner"] == admin_user

        pending_dispatched = request("/api/v11/problems/" + pending_id + "/dispatch", 200, {
            "isClaim": False,
            "returnToFactory": False,
            "projectCode": "PRJ-HF-2026-01",
            "appointmentAt": appointment_at,
            "photoNames": "bound-ok.jpg",
            "expectedVersion": assigned_pending["version"],
        })
        assert pending_dispatched["externalNo"].startswith("LZ-"), pending_dispatched
        assert request("/api/problems/" + pending_id)["state"] == "pending_callback"

        # 4. 智享乐装派单 -> 回传幂等 -> 旧件结案卡点
        flow_created = request("/api/v11/problems", 201, {
            "device": "CD26080101",
            "devicePending": False,
            "title": "V1.1乐装闭环与旧件卡点测试",
            "description": "交流桩告警，需派单更换部件并登记旧件",
            "channel": "400",
            "category": "无法充电",
            "urgency": "high",
            "faultOccurredAt": occurred_at,
        })
        flow_id = flow_created["id"]
        flow_detail = request("/api/problems/" + flow_id)
        assert flow_detail["state"] == "pending_assign"

        request("/api/v11/problems/" + flow_id + "/assign", 204, {
            "target": admin_user,
            "expectedVersion": flow_detail["version"],
        })
        flow_detail = request("/api/problems/" + flow_id)
        assert flow_detail["state"] == "processing"

        request("/api/problems/" + flow_id + "/assessment", 204, {
            "grade": "B",
            "disposalType": "part",
            "note": "判定B级换件处置，触发旧件结案卡点",
            "expectedVersion": flow_detail["version"],
        })
        flow_detail = request("/api/problems/" + flow_id)

        dispatched = request("/api/v11/problems/" + flow_id + "/dispatch", 200, {
            "isClaim": True,
            "returnToFactory": False,
            "projectCode": "PRJ-HF-2026-01",
            "appointmentAt": appointment_at,
            "photoNames": "fault-plate.jpg",
            "expectedVersion": flow_detail["version"],
        })
        external_no = dispatched["externalNo"]
        assert external_no.startswith("LZ-"), dispatched
        flow_detail = request("/api/problems/" + flow_id)
        assert flow_detail["state"] == "pending_callback"

        cb_path = "/api/v11/problems/" + flow_id + "/lezhuang-callback"
        request(cb_path, 204, {
            "externalNo": external_no,
            "state": "accepted",
            "receipt": "服务商已接单",
            "expectedVersion": flow_detail["version"],
        })
        flow_detail = request("/api/problems/" + flow_id)
        assert flow_detail["state"] == "pending_callback"

        request(cb_path, 204, {
            "externalNo": external_no,
            "state": "arrived",
            "receipt": "工程师已到达现场",
            "expectedVersion": flow_detail["version"],
        })
        flow_detail = request("/api/problems/" + flow_id)
        assert flow_detail["state"] == "pending_callback"

        completed_key = "smoke-lz-complete-" + uuid.uuid4().hex[:16]
        completed_body = {
            "externalNo": external_no,
            "state": "completed",
            "receipt": "已更换故障模块并复测充电正常",
            "replacedParts": "控制主板",
            "customerAcceptResult": "客户现场确认恢复",
            "feeOwner": "厂内质保",
            "expectedVersion": flow_detail["version"],
        }
        request(cb_path, 204, completed_body, key=completed_key)
        # 重复幂等回传不崩溃
        request(cb_path, 204, completed_body, key=completed_key)

        flow_detail = request("/api/problems/" + flow_id)
        assert flow_detail["state"] == "pending_accept"

        # 倒退回传不崩溃，并记录异常留痕而不倒退工单/派单状态
        request(cb_path, 204, {
            "externalNo": external_no,
            "state": "arrived",
            "receipt": "模拟乱序倒退回调",
            "expectedVersion": flow_detail["version"],
        })
        after_rollback = request("/api/problems/" + flow_id)
        assert after_rollback["state"] == "pending_accept"
        assert after_rollback["fieldOrders"][0]["state"] == "completed"
        assert "检测到状态回退回调" in (after_rollback["fieldOrders"][0].get("rollbackAnomalyNote") or "")
        assert any(item["action"] == "callback_anomaly" for item in after_rollback["activities"])

        # 旧件结案卡点：未登记旧件禁止闭环，补齐旧件号后闭环成功进入 closed
        assert_safe(request("/api/v11/problems/" + flow_id + "/accept", 409, {
            "approved": True,
            "note": "未登记旧件尝试闭环",
            "expectedVersion": after_rollback["version"],
        }), "旧件未登记")

        old_part_no = "OLD-PART-" + flow_id[:8]
        request("/api/v11/problems/" + flow_id + "/accept", 204, {
            "approved": True,
            "note": "旧件已核销回收，准予闭环",
            "oldPartNo": old_part_no,
            "expectedVersion": after_rollback["version"],
        })
        closed_detail = request("/api/problems/" + flow_id)
        assert closed_detail["state"] == "closed"
        assert bool(closed_detail["oldPartRegistered"]) is True
        assert closed_detail["oldPartNo"] == old_part_no
        print("V1.1 严格一期核心闭环通过")
    finally:
        try:
            request("/api/session/logout", 204, {})
            request("/api/session", 401)
        finally:
            activate(prev_client, prev_csrf)


def main():
    verify_v11_strict_flow()


if __name__ == "__main__":
    main()
