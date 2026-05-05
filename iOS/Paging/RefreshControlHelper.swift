//
//  RefreshControlHelper.swift
//  Paging
//
//  Created by 홍희표 on 5/5/26.
//

import Foundation
import SwiftUI
import UIKit

struct RefreshControlHelper: UIViewRepresentable {
    let onRefresh: () -> Void // async 제거 (라이브러리 함수 호출용)
    @Binding var isRefreshing: Bool

    func makeCoordinator() -> Coordinator {
        Coordinator(onRefresh: onRefresh, isRefreshing: $isRefreshing)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isHidden = true
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        DispatchQueue.main.async {
            // 부모 UIScrollView 찾기
            guard let scrollView = uiView.ancestor(ofType: UIScrollView.self) else { return }
            
            if scrollView.refreshControl == nil {
                let refreshControl = UIRefreshControl()
                refreshControl.addTarget(context.coordinator, action: #selector(Coordinator.handleRefresh), for: .valueChanged)
                scrollView.refreshControl = refreshControl
            }

            // 라이브러리의 상태(isRefreshing)에 따라 UI 업데이트
            if isRefreshing {
                if !(scrollView.refreshControl?.isRefreshing ?? false) {
                    scrollView.refreshControl?.beginRefreshing()
                }
            } else {
                scrollView.refreshControl?.endRefreshing()
            }
        }
    }

    class Coordinator: NSObject {
        let onRefresh: () -> Void
        @Binding var isRefreshing: Bool

        init(onRefresh: @escaping () -> Void, isRefreshing: Binding<Bool>) {
            self.onRefresh = onRefresh
            self._isRefreshing = isRefreshing
        }

        @objc func handleRefresh(_ sender: UIRefreshControl) {
            // 1. 상태를 true로 변경
            isRefreshing = true
            // 2. 새로고침 함수 실행 (명령 전달)
            onRefresh()
        }
    }
}

// 부모 뷰를 찾기 위한 확장
extension UIView {
    func ancestor<T: UIView>(ofType type: T.Type) -> T? {
        var current: UIView? = self
        while let next = current?.superview {
            if let target = next as? T { return target }
            current = next
        }
        return nil
    }
}
