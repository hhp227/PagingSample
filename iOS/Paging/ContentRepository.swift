//
//  ContentRepository.swift
//  Paging
//
//  Created by 홍희표 on 2022/06/19.
//

import Foundation
import Combine

class ContentRepository {
    private let movieApi: MovieApi
    
    func getPopularMovies(page: Int, apiKey: String) async throws -> MovieListResponse {
        return try await movieApi.getPopularMovies(page: page, apiKey: apiKey)
    }
    
    init(_ movieApi: MovieApi) {
        self.movieApi = movieApi
    }
}
