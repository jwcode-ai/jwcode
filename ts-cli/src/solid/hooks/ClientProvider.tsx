/**
 * ClientProvider — JwCodeClient lifecycle.
 * Replaces React's useConnection.ts + useWebSocket.ts.
 *
 * This only handles connect/disconnect lifecycle and connection-level
 * events (reconnect, error). Detailed stream event wiring is in
 * useStreamHandlers which should be called inside App.tsx after mount.
 */
import { createContext, onCleanup, useContext, type ParentProps } from "solid-js"
import { JwCodeClient } from "../../client"
import { useAppStateContext } from "./AppStateProvider"

interface ClientContextValue {
  client: JwCodeClient
}

const ctx = createContext<ClientContextValue>()

// Global reference for exit handlers (SIGINT in main.tsx)
export let _globalClient: JwCodeClient | null = null

export function ClientProvider(
  props: ParentProps<{
    backendUrl: string
    wsUrl: string
    token?: string
  }>,
) {
  const { setState } = useAppStateContext()
  const client = new JwCodeClient(props.backendUrl, props.wsUrl, props.token ?? "default-token")
  _globalClient = client
  onCleanup(() => { _globalClient = null })

  // Connect and update basic connection state
  client
    .connect()
    .then(async () => {
      setState("connected", true)
      setState("statusText", "")
      try {
        const r = await fetch(`${props.backendUrl}/api/models`)
        const d = (await r.json()) as any
        const models = d.data?.models
        if (models?.[0]?.name) setState("modelName", models[0].name)
      } catch {
        // Non-critical — model name stays empty
      }
    })
    .catch((err: Error) => {
      setState("statusText", `Connection failed: ${err.message}`)
    })

  // Connection-level events
  client.on("notification", (m: { data?: string | null }) => {
    const text = m.data ?? ""
    if (text === "Reconnected.") setState("connected", true)
    setState("statusText", text)
  })

  client.on("disconnected", () => {
    setState("connected", false)
    setState("statusText", "Connection lost — reconnecting...")
  })

  client.on("error", (m: { data?: string | null }) => {
    const text = String(m.data ?? "Error").slice(0, 120)
    setState("statusText", `Error: ${text}`)
  })

  onCleanup(() => {
    client.close()
  })

  return <ctx.Provider value={{ client }}>{props.children}</ctx.Provider>
}

export function useClient() {
  const v = useContext(ctx)
  if (!v) throw new Error("ClientProvider not found")
  return v.client
}
