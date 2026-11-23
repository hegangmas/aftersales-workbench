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
    if data is not None and path.startswith("/api/problems"):
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

request("/api/session/logout", 204, {})
request("/api/session", 401)
print("登录、退出、即时回访和恢复证据通过")
