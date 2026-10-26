// 与 Vue 解耦，方便测试断线恢复、订阅替换与请求合并。
export function createRefreshQueue(task, { later = setTimeout, cancel = clearTimeout } = {}) {
  let timer = null, running = false, dirty = false, stopped = false
  function request() {
    if (stopped) return
    dirty = true
    if (!running && timer === null) timer = later(run, 120)
  }
  async function run() {
    timer = null
    if (stopped || !dirty) return
    running = true; dirty = false
    try { await task() } catch { /* 后台补查失败保留已有数据，重连或回到页面再试。 */ }
    finally { running = false; if (dirty && !stopped) request() }
  }
  return { request, stop() { stopped = true; dirty = false; if (timer !== null) cancel(timer); timer = null } }
}

export function createProductChannel({ url, onChange, onReady, Socket = WebSocket,
  later = setTimeout, cancel = clearTimeout, every = setInterval, cancelEvery = clearInterval }) {
  let socket = null, ids = [], retry = null, heartbeat = null, handshake = null, stopped = true, generation = 0, attempts = 0
  const sendSubscription = () => { if (socket?.readyState === 1) socket.send(JSON.stringify({ type: 'subscribe', productIds: ids })) }
  function connect() {
    if (stopped || !ids.length || socket) return
    const own = ++generation
    const current = new Socket(url); socket = current
    let waitingForPong = false
    const closed = () => {
      if (own !== generation) return
      generation++; socket = null
      if (heartbeat !== null) cancelEvery(heartbeat)
      if (handshake !== null) cancel(handshake)
      heartbeat = null; handshake = null
      if (!stopped && retry === null) retry = later(() => { retry = null; connect() }, Math.min(1000 * 2 ** attempts++, 15000))
    }
    const recover = () => { closed(); current.close() }
    handshake = later(() => { if (own === generation && current.readyState !== 1) recover() }, 10000)
    current.onopen = () => {
      if (own !== generation || stopped) return
      if (handshake !== null) cancel(handshake)
      handshake = null
      attempts = 0; sendSubscription()
      heartbeat = every(() => {
        if (waitingForPong || current.readyState !== 1) { recover(); return }
        waitingForPong = true
        try { current.send('ping') } catch { recover() }
      }, 25000)
    }
    current.onmessage = event => {
      if (own !== generation || stopped) return
      if (event.data === 'pong') { waitingForPong = false; return }
      let message
      try { message = JSON.parse(event.data) } catch { return }
      if (message.type === 'subscribed') onReady?.()
      if (message.type === 'product_changed' && ids.includes(Number(message.productId))) onChange?.(Number(message.productId))
    }
    current.onclose = closed
    current.onerror = () => { if (own === generation) recover() }
  }
  function stop() {
    stopped = true; generation++
    if (retry !== null) cancel(retry)
    if (heartbeat !== null) cancelEvery(heartbeat)
    if (handshake !== null) cancel(handshake)
    retry = null; heartbeat = null; handshake = null
    const previous = socket; socket = null; previous?.close()
  }
  return {
    start(values) {
      ids = [...new Set(values.map(Number).filter(id => Number.isSafeInteger(id) && id > 0))].slice(0, 50)
      if (!ids.length) { stop(); return }
      stopped = false
      if (socket) sendSubscription(); else connect()
    }, stop
  }
}
