//
//  ContentView.swift
//  Paging
//
//  Created by 홍희표 on 2022/06/19.
//

import SwiftUI
import Combine

struct ContentView: View {
    @StateObject var viewModel = ContentViewModel(.init(MovieApi.create()))
    
    var body: some View {
        NavigationView {
            MovieList(
                pagingDataPublisher: viewModel.pagingDataPublisher,
                onItemClick: viewModel.onItemClick
            )
            .navigationBarTitleDisplayMode(.inline)
            .navigationTitle("Test")
        }
    }
}

struct MovieList: View {
    @StateObject private var lazyPagingItems: LazyPagingItems<Movie>

    let onItemClick: (Movie?) -> Void
    
    init(
        pagingDataPublisher: AnyPublisher<PagingData<Movie>, Never>,
        onItemClick: @escaping (Movie?) -> Void
    ) {
        _lazyPagingItems = StateObject(wrappedValue: LazyPagingItems(pagingDataPublisher))
        self.onItemClick = onItemClick
    }

    var body: some View {
        ScrollView {
            LazyVStack {
                ForEach(0..<lazyPagingItems.itemCount, id: \.self) { index in
                    MovieItem(movie: lazyPagingItems.get(index), onItemClick: onItemClick)
                }
                HStack {
                    if lazyPagingItems.loadState.refresh is LoadState.Loading {
                        Text("LoadingView")
                    } else if lazyPagingItems.loadState.append is LoadState.Loading {
                        ProgressView()
                    } else if lazyPagingItems.loadState.refresh is LoadState.Error {
                        
                    } else if lazyPagingItems.loadState.append is LoadState.Error {
                        
                    }
                }
            }
        }
        .refreshable(action: lazyPagingItems.refresh)
        .task {
            lazyPagingItems.startCollecting()
        }
    }
}

struct MovieItem: View {
    let movie: Movie?

    let onItemClick: (Movie?) -> Void

    var body: some View {
        HStack {
            MovieTitle(title: movie?.title ?? "", onItemClick: { onItemClick(movie) })
        }
        .padding(EdgeInsets(top: 16, leading: 16, bottom: 0, trailing: 16))
    }
}

struct MovieTitle: View {
    let title: String

    let onItemClick: () -> Void

    var body: some View {
        VStack {
            Button(action: onItemClick, label: { Text(title) })
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
