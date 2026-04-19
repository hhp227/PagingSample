//
//  MovieListResponse.swift
//  Paging
//
//  Created by 홍희표 on 2022/06/19.
//

import Foundation

struct MovieListResponse: Codable, Identifiable {
    var id: Int
    
    var totalPages: Int
    
    var totalResults: Int
    
    var results: [Movie]
    
    static let EMPTY = MovieListResponse(id: 0, totalPages: 0, totalResults: 0, results: [])
    
    enum CodingKeys: String, CodingKey {
        case results
        case id = "page"
        case totalPages = "total_pages"
        case totalResults = "total_results"
    }
}
