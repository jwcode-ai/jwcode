import { type Message } from '../protocol.js';
interface Props {
    messages: Message[];
    currentMessage: Message | null;
    scrollOffset: number;
    terminalRows: number;
    reservedRows: number;
}
export declare function ChatArea({ messages, currentMessage, scrollOffset, terminalRows, reservedRows }: Props): import("react/jsx-runtime").JSX.Element;
export {};
