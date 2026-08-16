import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';

function wsOrigin() {
  const origin = process.env.NEXT_PUBLIC_API_ORIGIN ?? 'http://localhost:8080';
  return origin.replace(/^http/, 'ws');
}

export function createStompClient() {
  return new Client({
    brokerURL: `${wsOrigin()}/ws`,
    reconnectDelay: 2000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  });
}

export function subscribeJson<T>(
  client: Client,
  destination: string,
  onMessage: (body: T) => void,
): StompSubscription {
  return client.subscribe(destination, (message: IMessage) => {
    onMessage(JSON.parse(message.body));
  });
}

export function sendJson(client: Client, destination: string, body: unknown) {
  client.publish({ destination, body: JSON.stringify(body) });
}
