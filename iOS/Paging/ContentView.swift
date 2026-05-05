//
//  ContentView.swift
//  Paging
//
//  Created by 홍희표 on 2022/06/19.
//

import SwiftUI

struct ContentView: View {
    @StateObject var viewModel = ContentViewModel(.init(MovieApi.create()))
    
    var body: some View {
        NavigationView {
            MovieList(
                lazyPagingItems: viewModel.$state.map { $0.pagingData }.collectAsLazyPagingItems(),
                onItemClick: viewModel.onItemClick
            )
            .navigationBarTitleDisplayMode(.inline)
            .navigationTitle("Test")
        }
    }
}

struct MovieList: View {
    @StateObject var lazyPagingItems: LazyPagingItems<Movie>

    let onItemClick: (Movie?) -> Void
    
    @State private var isRefreshing = false

    var body: some View {
        /*ScrollView {
            LazyVStack {
                ForEach(lazyPagingItems) { movie in
                    MovieItem(movie: movie, onItemClick: onItemClick)
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
        .refreshable(action: lazyPagingItems.refresh)*/
        ScrollView { // SwiftUI 순정 ScrollView 사용 (LazyVStack 성능 보존)
            ZStack {
                // 이 헬퍼가 UIScrollView를 찾아 RefreshControl을 심어줍니다.
                RefreshControlHelper(onRefresh: {
                    lazyPagingItems.refresh()
                }, isRefreshing: $isRefreshing)
                
                LazyVStack(spacing: 0) {
                    ForEach(lazyPagingItems) { movie in
                        MovieItem(movie: movie, onItemClick: onItemClick)
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
