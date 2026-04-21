//
//  ContentViewModel.swift
//  Paging
//
//  Created by 홍희표 on 2022/06/19.
//

import Foundation
import Combine
import SwiftUI

class ContentViewModel: ObservableObject {
    static let API_KEY = "a86526535fa0fc12d269041691633aed"

    let repository: ContentRepository
    
    lazy var pagingDataPublisher: AnyPublisher<PagingData<Movie>, Never> = $state
        .map(\.pagingData)
        .eraseToAnyPublisher()

    lazy var movies: AnyPublisher<PagingData<Movie>, Never> = Pager(PagingConfig(pageSize: 10)) {
        ContentPagingSource(self.repository, ContentViewModel.API_KEY)
    }.publisher

    func onItemClick(_ movie: Movie?) {
        let pagingData = state.pagingData.filter { $0.id != movie?.id }
        state = state.copy(pagingData: pagingData)
        print("onItemClick: \(movie?.id)")
    }

    ///
    @Published var state = State()

    struct State {
        var pagingData: PagingData<Movie> = PagingData<Movie>.empty()

        var cancellables = [AnyCancellable]()
    }

    init(_ repository: ContentRepository) {
        self.repository = repository

        Pager(PagingConfig(pageSize: 10)) {
            ContentPagingSource(self.repository, ContentViewModel.API_KEY)
        }
        .publisher
        .cachedIn()
        .sink {
            self.state = self.state.copy(pagingData: $0)
        }
        .store(in: &state.cancellables)
    }
}

extension ContentViewModel.State {
    func copy(
        pagingData: PagingData<Movie>? = nil,
        cancellables: [AnyCancellable]? = nil
    ) -> ContentViewModel.State {
        .init(
            pagingData: pagingData ?? self.pagingData,
            cancellables: cancellables ?? self.cancellables
        )
    }
}
