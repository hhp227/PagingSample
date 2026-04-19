//
//  Movie.swift
//  Paging
//
//  Created by 홍희표 on 2022/06/19.
//

import Foundation

struct Movie: Codable, Identifiable {
    var adult: Bool
    
    var backdropPath: String?
    
    var genreIds: [Int]
    
    var id: Int
    
    var originalLanguage: String
    
    var originalTitle: String
    
    var overview: String
    
    var popularity: Double
    
    var releaseDate: String
    
    var title: String
    
    var video: Bool
    
    var voteAverage: Float
    
    var voteCount: Int
    
    enum CodingKeys: String, CodingKey {
        case adult, id, overview, popularity, title, video
        case backdropPath = "backdrop_path"
        case genreIds = "genre_ids"
        case originalLanguage = "original_language"
        case originalTitle = "original_title"
        case releaseDate = "release_date"
        case voteAverage = "vote_average"
        case voteCount = "vote_count"
    }
}
