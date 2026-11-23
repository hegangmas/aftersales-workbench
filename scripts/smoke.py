"""Exercise the first workflow against a running app and its test MySQL database."""
from datetime import datetime, timedelta, timezone
import json
import os
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("WORKBENCH_TEST_URL", "http://127.0.0.1:8780")


def request(path, status=200, body=None):
    data = None if body is None else json.dumps(body).encode()
    headers = {} if data is None else {"Content-Type": "application/json"}
    req = urllib.request.Request(BASE + path, data=data, headers=headers)
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as exc:
        response = exc
    with response:
        assert response.status == status, (path, response.status, response.read())
        payload = response.read()
    return json.loads(payload) if payload else None


device = request("/api/devices/lookup?query=19900000001")
assert device["device"] == "SIM-AC-001" and device["fault"]
request("/api/devices/lookup?query=UNKNOWN", 404)
request("/api/problems", 400, {"device": device["device"], "title": "", "description": "test"})
created = request("/api/problems", 201, {
    "device": device["device"], "title": "回归测试", "description": "车辆连接后未开始充电"
})
path = "/api/problems/" + urllib.parse.quote(created["id"])
request(path + "/followups", 204, {"note": "已与车主确认故障现象"})
request(path + "/followups", 400, {"note": " "})
problem = request(path)
assert problem["device"] == device["device"] and problem["state"] == "open"
assert [item["action"] for item in problem["activities"]] == ["create", "followup"]
assert problem["activities"][-1]["note"] == "已与车主确认故障现象"
print("首条链路通过，回归问题单 " + problem["id"])

assert created["id"] in {item["id"] for item in request("/api/problems?device=SIM-AC-001")}
request("/api/problems/due")
callback = path + "/callback"
due = datetime.now(timezone(timedelta(hours=8))).replace(tzinfo=None) + timedelta(hours=1)
schedule = {"owner": "测试坐席", "nextStep": "确认设备是否恢复", "dueAt": due.isoformat(timespec="minutes")}
request(callback, 400, {**schedule, "dueAt": "2000-01-01T10:00"})
request(callback, 204, schedule)
pending = request(path)
assert pending["state"] == "waiting" and pending["pendingFollowup"]["owner"] == schedule["owner"]
request(callback + "/result", 400, {"outcome": "unknown", "note": "校验回滚"})
assert request(path)["pendingFollowup"]["owner"] == schedule["owner"]
request(callback + "/result", 204, {"outcome": "continue", "note": "重新约定时间"})
assert request(path)["state"] == "open"
request(callback, 204, schedule)
request(callback + "/result", 204, {"outcome": "field", "note": "模拟现场检测"})
assert request(path)["state"] == "field"
request(callback, 204, schedule)
request(callback + "/result", 204, {"outcome": "resolved", "note": "模拟车主确认已恢复充电"})
final = request(path)
assert final["state"] == "resolved" and final["pendingFollowup"] is None
request(callback, 409, schedule)
print("回访三种去向、事务回滚与已结案保护通过")
