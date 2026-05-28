interface Props {
    toolName: string;
    payload: string;
    onAllow: () => void;
    onDeny: () => void;
}
export declare function ApprovalModal({ toolName, payload, onAllow, onDeny }: Props): import("react/jsx-runtime").JSX.Element;
export {};
