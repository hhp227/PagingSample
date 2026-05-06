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
    let isRefreshing: Bool

    func makeCoordinator() -> Coordinator {
        Coordinator(onRefresh: onRefresh)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isHidden = true
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        DispatchQueue.main.async {
            context.coordinator.onRefresh = onRefresh
            
            // 부모 UIScrollView 찾기
            guard let scrollView = uiView.ancestor(ofType: UIScrollView.self) else { return }
            
            if scrollView.refreshControl == nil {
                let refreshControl = UIRefreshControl()
                refreshControl.addTarget(context.coordinator, action: #selector(Coordinator.handleRefresh), for: .valueChanged)
                scrollView.refreshControl = refreshControl
            }

            // 라이브러리의 상태(isRefreshing)에 따라 UI 업데이트
            if isRefreshing {
                context.coordinator.wasRefreshing = scrollView.refreshControl?.isRefreshing == true
            } else {
                context.coordinator.endRefreshingIfNeeded(in: scrollView)
            }
        }
    }

    class Coordinator: NSObject {
        var onRefresh: () -> Void
        var wasRefreshing = false

        init(onRefresh: @escaping () -> Void) {
            self.onRefresh = onRefresh
        }

        @objc func handleRefresh(_ sender: UIRefreshControl) {
            // 새로고침 명령 전달. 표시 상태는 외부 loadState로 제어
            onRefresh()
        }
        
        func endRefreshingIfNeeded(in scrollView: UIScrollView) {
            guard wasRefreshing || scrollView.refreshControl?.isRefreshing == true else { return }
            
            wasRefreshing = false
            scrollView.layoutIfNeeded()
            scrollView.refreshControl?.endRefreshing()
            scrollView.layoutIfNeeded()
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
