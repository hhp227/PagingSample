//
//  MovieApi.swift
//  Paging
//
//  Created by 홍희표 on 2022/06/19.
//

import Foundation
import Combine

class MovieApi {
    static let baseUrl = "https://api.themoviedb.org/"
    
    func getPopularMovies(page: Int, apiKey: String) async throws -> MovieListResponse {
        guard let url = URL(string: "\(MovieApi.baseUrl)3/movie/popular?page=\(page)&api_key=\(apiKey)") else {
            fatalError()
        }
        let (data, response) = try! await URLSession.shared.data(for: URLRequest(url: url))
        guard let response = response as? HTTPURLResponse, (200..<300).contains(response.statusCode) else {
            fatalError(response.description)
        }
        guard let movieListResponse = try? JSONDecoder().decode(MovieListResponse.self, from: data) else {
            throw MyError.jsonDecodeError(message: "json decode error occur")
        }
        return movieListResponse
    }
    
    private static var instance: MovieApi? = nil
    
    static func create() -> MovieApi {
        if let instance = self.instance {
            return instance
        } else {
            let movieApi = MovieApi()
            self.instance = movieApi
            return movieApi
        }
    }
}

enum MyError: Error {
case jsonDecodeError(message: String)
}
