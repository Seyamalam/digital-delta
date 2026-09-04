"""Simulated flood controls for local development only.

This HTTP/JSON fixture never participates in the Protobuf mesh. Every mutation is
marked as simulated and exists only to help a presenter exercise failure states.
"""

from __future__ import annotations

import copy
import json
import random
import threading
import time
from pathlib import Path

from flask import Flask, jsonify


MAP_FILE = Path(__file__).with_name("sylhet_map.json")
CHAOS_INTERVAL_SECONDS = 30

app = Flask(__name__)
state_lock = threading.Lock()


def load_map() -> dict:
    with MAP_FILE.open(encoding="utf-8") as source:
        data = json.load(source)
    data["metadata"]["simulation"] = True
    data["metadata"]["source"] = "local_development_fixture"
    return data


map_data = load_map()


def apply_chaos_step(generator: random.Random = random) -> str:
    """Apply one simulated flood or recovery event and return its narration."""
    with state_lock:
        roads = [edge for edge in map_data["edges"] if edge["type"] == "road"]
        safe_roads = [edge for edge in roads if not edge["is_flooded"]]
        flooded_roads = [edge for edge in roads if edge["is_flooded"]]
        event_type = generator.choices(["flood", "recede"], weights=[0.6, 0.4])[0]

        if event_type == "flood" and safe_roads:
            target = generator.choice(safe_roads)
            target["is_flooded"] = True
            target["original_weight"] = target.get("base_weight_mins", 45)
            target["base_weight_mins"] = 9999
            return f"SIMULATED_FLOOD:{target['id']}:{target['source']}->{target['target']}"
        if event_type == "recede" and flooded_roads:
            target = generator.choice(flooded_roads)
            target["is_flooded"] = False
            target["base_weight_mins"] = target.get("original_weight", 45)
            return f"SIMULATED_RECOVERY:{target['id']}:{target['source']}->{target['target']}"
        return "SIMULATED_STABLE"


def trigger_chaos() -> None:
    while True:
        time.sleep(CHAOS_INTERVAL_SECONDS)
        print(apply_chaos_step(), flush=True)


@app.get("/api/network/status")
def get_network_status():
    with state_lock:
        return jsonify(copy.deepcopy(map_data))


@app.post("/api/network/reset")
def reset_network():
    global map_data
    with state_lock:
        map_data = load_map()
        snapshot = copy.deepcopy(map_data)
    return jsonify({"status": "success", "simulated": True, "network": snapshot})


if __name__ == "__main__":
    threading.Thread(target=trigger_chaos, daemon=True).start()
    print("Digital Delta simulated chaos fixture is running on port 5000", flush=True)
    app.run(debug=False, host="127.0.0.1", port=5000)
