// Module-level client reference for components that need it outside React context
let _client = null;
export function setClient(client) {
    _client = client;
}
export function getClient() {
    return _client;
}
