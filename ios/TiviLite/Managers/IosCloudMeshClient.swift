import Foundation
import Network

public class IosCloudMeshClient: ObservableObject {
    public static let shared = IosCloudMeshClient()

    @Published public var discoveredPeers: [MeshPeer] = []
    private var listener: NWListener?

    public struct MeshPeer: Identifiable, Hashable {
        public let id: String
        public let name: String
        public let host: String
        public let formFactor: String
        public var currentChannel: String?
    }

    private init() {
        startDiscovery()
    }

    public func startDiscovery() {
        // UDP broadcast listener for TiviLite Cloud Mesh
        do {
            let params = NWParameters.udp
            params.allowLocalEndpointReuse = true
            listener = try NWListener(using: params, on: 9876)
            listener?.newConnectionHandler = { [weak self] connection in
                connection.start(queue: .main)
                self?.receive(on: connection)
            }
            listener?.start(queue: .main)
        } catch {
            print("Failed to start Mesh listener: \(error.localizedDescription)")
        }
    }

    private func receive(on connection: NWConnection) {
        connection.receiveMessage { [weak self] content, context, isComplete, error in
            if let data = content, let text = String(data: data, encoding: .utf8) {
                self?.handleMeshMessage(text)
            }
        }
    }

    private func handleMeshMessage(_ message: String) {
        // Parse TiviLite Cloud Mesh discovery packet
        // { "action": "MESH_BEACON", "deviceId": "...", "name": "客厅激光电视", "formFactor": "TV" }
        guard let data = message.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let action = json["action"] as? String, action == "MESH_BEACON",
              let deviceId = json["deviceId"] as? String,
              let name = json["name"] as? String else { return }

        let peer = MeshPeer(
            id: deviceId,
            name: name,
            host: json["ip"] as? String ?? "127.0.0.1",
            formFactor: json["formFactor"] as? String ?? "TV",
            currentChannel: json["channel"] as? String
        )

        DispatchQueue.main.async {
            if !self.discoveredPeers.contains(where: { $0.id == peer.id }) {
                self.discoveredPeers.append(peer)
            }
        }
    }

    public func sendHandoff(to peer: MeshPeer, channel: ChannelItem) {
        // Send handoff command to target peer
        let payload: [String: Any] = [
            "action": "MESH_HANDOFF",
            "channelName": channel.name,
            "channelUrl": channel.url,
            "targetDeviceId": peer.id
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: payload) else { return }

        let host = NWEndpoint.Host(peer.host)
        let port = NWEndpoint.Port(rawValue: 9876)!
        let conn = NWConnection(host: host, port: port, using: .udp)
        conn.start(queue: .main)
        conn.send(content: data, completion: .contentProcessed({ _ in
            conn.cancel()
        }))
    }
}
