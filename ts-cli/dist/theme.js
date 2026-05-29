const dark = {
    bg: '',
    text: '',
    muted: 'grey',
    border: 'grey',
    primary: 'cyan',
    success: 'green',
    warning: 'yellow',
    error: 'red',
    info: 'blue',
    user: 'green',
    assistant: '',
    system: 'red',
    tool: 'magenta',
    thinking: 'grey',
    plan: 'yellow',
    auto: 'magenta',
    connected: 'green',
    disconnected: 'red',
};
const light = {
    ...dark,
    muted: 'blackBright',
};
const themes = { dark, light };
function detectTheme() {
    if (process.env.JWCODE_THEME === 'light')
        return 'light';
    return 'dark';
}
let current = detectTheme();
export function getTheme() {
    return themes[current];
}
export function setTheme(name) {
    current = name;
}
export const t = getTheme();
