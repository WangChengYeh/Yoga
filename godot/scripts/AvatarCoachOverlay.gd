extends Control

@onready var avatar_controller = $AvatarController

const PORT = 9090
var tcp_server := TCPServer.new()
var clients: Array[WebSocketPeer] = []

func _ready():
    if tcp_server.listen(PORT) == OK:
        print("AvatarCoachOverlay listening on port ", PORT)
    else:
        printerr("Failed to start AvatarCoachOverlay server on port ", PORT)

func _process(_delta):
    if tcp_server.is_connection_available():
        var tcp_peer = tcp_server.take_connection()
        var ws_peer = WebSocketPeer.new()
        ws_peer.accept_stream(tcp_peer)
        clients.append(ws_peer)
        print("GodotAvatarBridge connected.")

    for i in range(clients.size() - 1, -1, -1):
        var peer = clients[i]
        peer.poll()
        var state = peer.get_ready_state()
        if state == WebSocketPeer.STATE_OPEN:
            while peer.get_available_packet_count() > 0:
                var packet = peer.get_packet()
                var text = packet.get_string_from_utf8()
                _handle_message(text)
        elif state == WebSocketPeer.STATE_CLOSED:
            clients.remove_at(i)
            print("GodotAvatarBridge disconnected.")

func _handle_message(text: String):
    var json = JSON.new()
    var err = json.parse(text)
    if err == OK:
        var data = json.get_data()
        if typeof(data) == TYPE_DICTIONARY:
            if avatar_controller:
                avatar_controller.apply_pose_coach_frame(data)
