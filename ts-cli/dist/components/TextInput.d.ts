export declare function saveToHistory(text: string): void;
interface Props {
    value: string;
    onChange: (value: string) => void;
    onSubmit: (value: string) => void;
    placeholder?: string;
    disabled?: boolean;
}
export declare function TextInput({ value, onChange, onSubmit, placeholder, disabled }: Props): import("react/jsx-runtime").JSX.Element;
export {};
