//
//  PagingDataTransforms.swift
//  Paging
//
//  Created by hhp227 on 6/17/24.
//

import Foundation
import Combine

extension PagingData {

    public func transform<R: Any>(_ transform: @escaping (PageEvent<T>) -> PageEvent<R>) -> PagingData<R> {
        return PagingData<R>(
            publisher
                .map { transform($0) }
                .handleEvents(receiveOutput: { print("map \($0)") })
                .eraseToAnyPublisher(),
            receiver,
            hintReceiver
        )
    }
    
    public func filter(_ predicate: @escaping (T) -> Bool) -> PagingData<T> {
        return transform { $0.filter(predicate) }
    }
}
