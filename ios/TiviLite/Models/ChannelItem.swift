import Foundation

public struct ChannelItem: Identifiable, Hashable, Codable {
    public let id: String
    public let number: Int
    public let name: String
    public let group: String
    public let logoUrl: String?
    public let url: String
    public var isFavorite: Bool
    public var currentProgram: String?

    public init(id: String = UUID().uuidString, number: Int, name: String, group: String = "默认分组", logoUrl: String? = nil, url: String, isFavorite: Bool = false, currentProgram: String? = nil) {
        self.id = id
        self.number = number
        self.name = name
        self.group = group
        self.logoUrl = logoUrl
        self.url = url
        self.isFavorite = isFavorite
        self.currentProgram = currentProgram
    }
}
