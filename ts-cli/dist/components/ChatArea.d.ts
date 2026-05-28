import { type Message } from '../protocol.js';
interface Props {
    messages: Message[];
    currentMessage: Message | null;
}
export declare function ChatArea({ messages, currentMessage }: Props): import("react/jsx-runtime").JSX.Element;
export {};
