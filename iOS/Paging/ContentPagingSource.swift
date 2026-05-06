//
//  ContentPagingSource.swift
//  Paging
//
//  Created by 홍희표 on 2022/06/19.
//

import Foundation
import Combine

class ContentPagingSource: PagingSource<Int, Movie> {
    private let repository: ContentRepository
    
    private let apiKey: String
    
    private var subscriptions = Set<AnyCancellable>()
    
    override func getRefreshKey(state: PagingState<Int, Movie>) -> Int? {
        if let anchorPosition = state.anchorPosition {
            if let prevKey = state.closestPageToPosition(anchorPosition)?.prevKey {
                return prevKey + 1
            } else if let nextKey = state.closestPageToPosition(anchorPosition)?.nextKey {
                return nextKey - 1
            } else {
                return nil
            }
        } else {
            return nil
        }
    }
    
    override func load(params: PagingSource<Int, Movie>.LoadParams<Int>) async -> PagingSource<Int, Movie>.LoadResult<Int, Movie> {
        let nextPage = params.getKey() ?? 1

        //print("load: \(nextPage), movieListResponse: \(movieListResponse.results.count)")
        do {
            let movieListResponse = try await repository.getPopularMovies(page: nextPage, apiKey: apiKey)
            try await Task.sleep(nanoseconds: 1_000_000_000)
            return LoadResult<Int, Movie>.Page(
                data: movieListResponse.results,
                prevKey: nextPage == 1 ? nil : nextPage - 1,
                nextKey: movieListResponse.id + 1
            )
        } catch {
            if Task.isCancelled {
                return LoadResult<Int, Movie>.Invalid()
            }
            return LoadResult<Int, Movie>.Error(error: error)
        }
    }
    
    init(_ repository: ContentRepository, _ apiKey: String) {
        self.repository = repository
        self.apiKey = apiKey
    }
}

extension Optional where Wrapped == Int {
    mutating func plus(_ value: Int) {
        self! += value
    }
    
    mutating func minus(_ value: Int) {
        self! -= value
    }
}
