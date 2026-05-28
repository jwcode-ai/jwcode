import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { Box, Text, useInput } from 'ink';
export function TextInput({ value, onChange, onSubmit, placeholder, disabled }) {
    useInput((input, key) => {
        if (disabled)
            return;
        if (key.return) {
            onSubmit(value);
        }
        else if (key.backspace || key.delete) {
            onChange(value.slice(0, -1));
        }
        else if (input && input.length === 1 && !key.ctrl && !key.meta && !key.tab) {
            onChange(value + input);
        }
    });
    const display = value || '';
    const showPlaceholder = !display && placeholder;
    return (_jsxs(Box, { children: [_jsx(Text, { children: display }), showPlaceholder && _jsx(Text, { dimColor: true, children: placeholder })] }));
}
