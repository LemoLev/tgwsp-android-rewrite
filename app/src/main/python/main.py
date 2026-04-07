import backend
import asyncio
from typing import Optional

from java import static_proxy, jarray, jint, method, jvoid, jboolean
from java.lang import String

class ProxyControl(static_proxy()):
    def __init__(self):
        self.loop: Optional[asyncio.AbstractEventLoop] = None
        self.stop_event: Optional[asyncio.Event] = None

    @method(jvoid, [String, jint, String, String])
    def start_proxy(self, p_host: str, p_port: int, p_dcip: str, secret: str):
        cmd = ["--port", str(p_port), "--host", p_host, "--secret", secret]
        dcips = p_dcip.split("\n")

        cmd.append("--dc-ip")
        proxies = []
        for d in dcips:
            proxies.append(d)

        cmd.append(proxies)
        print(cmd)

        self.stop_event = asyncio.Event()
        self.loop = asyncio.new_event_loop()
        backend.main(cmd, self.stop_event, self.loop)

    @method(jvoid, [])
    def stop_proxy(self):
        if self.loop and self.stop_event:
            self.loop.call_soon_threadsafe(self.stop_event.set)
        print("Stop ProxyControl")
