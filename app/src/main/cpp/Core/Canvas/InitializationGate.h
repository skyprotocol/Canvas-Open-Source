#pragma once

#include <atomic>

namespace Canvas {

// Registry writes belong to the bootstrap thread, before publication to native readers.
class InitializationGate {
    enum class State { New, Loading, Ready };
    std::atomic<State> state{State::New};

public:
    bool begin() {
        State expected = State::New;
        return state.compare_exchange_strong(expected, State::Loading);
    }

    bool isLoading() const {
        return state.load(std::memory_order_acquire) == State::Loading;
    }

    bool publish() {
        State expected = State::Loading;
        return state.compare_exchange_strong(expected, State::Ready);
    }

    bool isReady() const {
        return state.load(std::memory_order_acquire) == State::Ready;
    }
};

}
