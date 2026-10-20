import Foundation
import AVFoundation
import AVKit
import MediaPlayer

public class IosPlayerManager: NSObject, ObservableObject, AVPictureInPictureControllerDelegate {
    public static let shared = IosPlayerManager()

    @Published public var player: AVPlayer?
    @Published public var isPlaying: Bool = false
    @Published public var currentChannel: ChannelItem?
    @Published public var isPipActive: Bool = false

    private var pipController: AVPictureInPictureController?

    override private init() {
        super.init()
        setupRemoteCommands()
    }

    public func play(channel: ChannelItem) {
        self.currentChannel = channel
        guard let streamUrl = URL(string: channel.url) else { return }

        let item = AVPlayerItem(url: streamUrl)
        if player == null {
            player = AVPlayer(playerItem: item)
        } else {
            player?.replaceCurrentItem(with: item)
        }

        player?.play()
        self.isPlaying = true
        updateNowPlaying(channel: channel)
    }

    public func togglePlayPause() {
        guard let p = player else { return }
        if isPlaying {
            p.pause()
            isPlaying = false
        } else {
            p.play()
            isPlaying = true
        }
        if let ch = currentChannel {
            updateNowPlaying(channel: ch)
        }
    }

    public func setupPip(playerLayer: AVPlayerLayer) {
        if AVPictureInPictureController.isPictureInPictureSupported() {
            pipController = AVPictureInPictureController(playerLayer: playerLayer)
            pipController?.delegate = self
        }
    }

    public func togglePip() {
        guard let pip = pipController else { return }
        if pip.isPictureInPictureActive {
            pip.stopPictureInPicture()
        } else {
            pip.startPictureInPicture()
        }
    }

    public func pictureInPictureControllerWillStartPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        isPipActive = true
    }

    public func pictureInPictureControllerDidStopPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        isPipActive = false
    }

    private func setupRemoteCommands() {
        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.playCommand.addTarget { [weak self] _ in
            self?.player?.play()
            self?.isPlaying = true
            return .success
        }
        commandCenter.pauseCommand.addTarget { [weak self] _ in
            self?.player?.pause()
            self?.isPlaying = false
            return .success
        }
        commandCenter.togglePlayPauseCommand.addTarget { [weak self] _ in
            self?.togglePlayPause()
            return .success
        }
    }

    private func updateNowPlaying(channel: ChannelItem) {
        var info = [String: Any]()
        info[MPMediaItemPropertyTitle] = channel.name
        info[MPMediaItemPropertyArtist] = channel.currentProgram ?? "TiviLite 高清直播"
        info[MPMediaItemPropertyAlbumTitle] = "TiviLite 灵动随身视听"
        info[MPNowPlayingInfoPropertyIsLiveStream] = true
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }
}
