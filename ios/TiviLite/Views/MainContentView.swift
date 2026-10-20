import SwiftUI
import AVKit

public struct MainContentView: View {
    @StateObject private var playerManager = IosPlayerManager.shared
    @StateObject private var meshClient = IosCloudMeshClient.shared

    @State private var channels: [ChannelItem] = [
        ChannelItem(number: 1, name: "CCTV-1 综合", url: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", isFavorite: true, currentProgram: "新闻联播"),
        ChannelItem(number: 2, name: "CCTV-2 财经", url: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", currentProgram: "天下财经"),
        ChannelItem(number: 4, name: "CCTV-4 中文国际", url: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", currentProgram: "中国新闻"),
        ChannelItem(number: 5, name: "CCTV-5 体育", url: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", isFavorite: true, currentProgram: "体育新闻"),
        ChannelItem(number: 6, name: "CCTV-6 电影", url: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", currentProgram: "经典影院"),
        ChannelItem(number: 8, name: "CCTV-8 电视剧", url: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", currentProgram: "黄金强档"),
        ChannelItem(number: 13, name: "CCTV-13 新闻", url: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", currentProgram: "共同关注"),
        ChannelItem(number: 21, name: "湖南卫视 高清", url: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", currentProgram: "综艺精选")
    ]

    @State private var selectedChannel: ChannelItem?
    @State private var showMeshSheet: Bool = false
    @State private var searchText: String = ""

    public init() {}

    public var body: some View {
        NavigationSplitView {
            // Sidebar for iPad / List for iPhone
            List(selection: $selectedChannel) {
                Section(header: Text("⭐ 收藏与常用频道")) {
                    ForEach(filteredChannels.filter { $0.isFavorite }) { ch in
                        channelRow(ch)
                    }
                }
                Section(header: Text("📺 全部聚合频道 (\(filteredChannels.count))")) {
                    ForEach(filteredChannels) { ch in
                        channelRow(ch)
                    }
                }
            }
            .navigationTitle("TiviLite 灵动")
            .searchable(text: $searchText, prompt: "搜索频道或节目...")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showMeshSheet = true }) {
                        Image(systemName: "tv.and.mediabox")
                            .foregroundColor(.cyan)
                    }
                }
            }
        } detail: {
            // Detail Player View
            ZStack {
                Color.black.edgesIgnoringSafeArea(.all)

                if let player = playerManager.player {
                    VideoPlayer(player: player)
                        .edgesIgnoringSafeArea(.all)
                } else {
                    VStack(spacing: 12) {
                        Image(systemName: "play.tv")
                            .font(.system(size: 60))
                            .foregroundColor(.gray)
                        Text("请选择频道开始播放")
                            .font(.headline)
                            .foregroundColor(.gray)
                    }
                }

                // Top Overlay Controls
                VStack {
                    HStack {
                        if let ch = selectedChannel {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(ch.number). \(ch.name)")
                                    .font(.headline)
                                    .bold()
                                    .foregroundColor(.white)
                                Text(ch.currentProgram ?? "高清直播")
                                    .font(.caption)
                                    .foregroundColor(.cyan)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(Color.black.opacity(0.6))
                            .cornerRadius(8)
                        }

                        Spacer()

                        // PiP Button
                        Button(action: { playerManager.togglePip() }) {
                            Image(systemName: "pip.enter")
                                .font(.system(size: 18))
                                .padding(8)
                                .background(Color.black.opacity(0.6))
                                .foregroundColor(.white)
                                .clipShape(Circle())
                        }
                    }
                    .padding()

                    Spacer()
                }
            }
        }
        .onAppear {
            if let first = channels.first {
                selectedChannel = first
                playerManager.play(channel: first)
            }
        }
        .sheet(isPresented: $showMeshSheet) {
            MeshPeersView(peers: meshClient.discoveredPeers) { targetPeer in
                if let ch = selectedChannel {
                    meshClient.sendHandoff(to: targetPeer, channel: ch)
                }
                showMeshSheet = false
            }
        }
    }

    private var filteredChannels: [ChannelItem] {
        if searchText.isEmpty { return channels }
        return channels.filter { $0.name.localizedCaseInsensitiveContains(searchText) }
    }

    private func channelRow(_ ch: ChannelItem) -> some View {
        HStack {
            Text(String(format: "%02d", ch.number))
                .font(.system(.subheadline, design: .monospaced))
                .foregroundColor(.cyan)
                .frame(width: 30, alignment: .leading)
            VStack(alignment: .leading, spacing: 2) {
                Text(ch.name)
                    .font(.body)
                    .bold()
                if let prog = ch.currentProgram {
                    Text(prog)
                        .font(.caption2)
                        .foregroundColor(.gray)
                }
            }
            Spacer()
            if ch.isFavorite {
                Image(systemName: "star.fill")
                    .foregroundColor(.yellow)
                    .font(.caption)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            selectedChannel = ch
            playerManager.play(channel: ch)
        }
    }
}

struct MeshPeersView: View {
    let peers: [IosCloudMeshClient.MeshPeer]
    let onSelect: (IosCloudMeshClient.MeshPeer) -> Void

    var body: some View {
        NavigationView {
            List(peers) { peer in
                HStack {
                    Image(systemName: "tv")
                        .foregroundColor(.cyan)
                    VStack(alignment: .leading) {
                        Text(peer.name).font(.headline)
                        Text("\(peer.formFactor) · \(peer.host)").font(.caption).foregroundColor(.gray)
                    }
                    Spacer()
                    Button("流转接力") {
                        onSelect(peer)
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
            .navigationTitle("☁️ 局域网自组网设备")
        }
    }
}
