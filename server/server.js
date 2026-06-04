const { WebSocketServer } = require('ws');

const wss = new WebSocketServer({ port: 8080 });
console.log('Matchmaking WebSocket server running on port 8080...');

let waitingSocket = null;
const rooms = new Map(); // socket -> room info

wss.on('connection', (ws) => {
    console.log('New client connected');

    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message);
            console.log('Received type:', data.type);

            if (data.type === 'strike') {
                const room = rooms.get(ws);
                if (room) {
                    const opponent = room.player1 === ws ? room.player2 : room.player1;
                    if (opponent && opponent.readyState === ws.OPEN) {
                        opponent.send(JSON.stringify({
                            type: 'strike',
                            vx: data.vx,
                            vy: data.vy
                        }));
                        console.log('Forwarded strike to opponent');
                    }
                }
            } else if (data.type === 'sync') {
                const room = rooms.get(ws);
                if (room) {
                    const opponent = room.player1 === ws ? room.player2 : room.player1;
                    if (opponent && opponent.readyState === ws.OPEN) {
                        opponent.send(JSON.stringify(data));
                        console.log('Forwarded sync packet to opponent');
                    }
                }
            }
        } catch (err) {
            console.error('Error handling message:', err);
        }
    });

    ws.on('close', () => {
        console.log('Client disconnected');
        if (waitingSocket === ws) {
            waitingSocket = null;
        } else {
            const room = rooms.get(ws);
            if (room) {
                const opponent = room.player1 === ws ? room.player2 : room.player1;
                rooms.delete(room.player1);
                rooms.delete(room.player2);
                if (opponent && opponent.readyState === ws.OPEN) {
                    opponent.send(JSON.stringify({ type: 'game_canceled', reason: 'Opponent disconnected' }));
                }
                console.log('Closed active game room due to disconnection');
            }
        }
    });

    // Matchmaking Logic
    if (waitingSocket && waitingSocket !== ws && waitingSocket.readyState === ws.OPEN) {
        const player1 = waitingSocket;
        const player2 = ws;
        waitingSocket = null;

        const room = { player1, player2 };
        rooms.set(player1, room);
        rooms.set(player2, room);

        // Player 1 role: starts first with turn
        player1.send(JSON.stringify({ type: 'start', role: 'P1', turn: true }));
        // Player 2 role: waits
        player2.send(JSON.stringify({ type: 'start', role: 'P2', turn: false }));
        console.log('Matched P1 and P2 into game room');
    } else {
        waitingSocket = ws;
        ws.send(JSON.stringify({ type: 'waiting' }));
        console.log('Player added to waiting queue');
    }
});
