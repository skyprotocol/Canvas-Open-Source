#include "Canvas/InitializationGate.h"

#include <atomic>
#include <cassert>
#include <condition_variable>
#include <mutex>
#include <thread>
#include <vector>

int main() {
    for (int run = 0; run < 100; ++run) {
        Canvas::InitializationGate gate;
        assert(!gate.isReady());
        assert(!gate.publish());
        assert(gate.begin());
        assert(gate.isLoading());
        assert(!gate.begin());

        std::vector<int> registry;
        std::mutex mutex;
        std::condition_variable changed;
        bool reading = false;
        bool release = false;
        std::thread lateReader([&] {
            while (!gate.isReady()) std::this_thread::yield();
            // Read data published by the bootstrap thread, without a registry lock.
            assert(registry.size() == 3);
            const int& item = registry[1];
            std::unique_lock<std::mutex> lock(mutex);
            reading = true;
            changed.notify_one();
            changed.wait(lock, [&] { return release; });
            assert(item == 20);
            assert(registry.size() == 3);
        });
        registry = {10, 20, 30};
        assert(gate.publish());
        {
            std::unique_lock<std::mutex> lock(mutex);
            changed.wait(lock, [&] { return reading; });
            // A new bootstrap cannot reach registry mutation while a reader holds an item.
            assert(!gate.begin());
            assert(!gate.isLoading());
            assert(!gate.publish());
            release = true;
        }
        changed.notify_one();
        lateReader.join();
    }

    Canvas::InitializationGate gate;
    std::atomic<int> winners{0};
    std::vector<std::thread> contenders;
    for (int i = 0; i < 16; ++i) {
        contenders.emplace_back([&] { if (gate.begin()) ++winners; });
    }
    for (auto& contender : contenders) contender.join();
    assert(winners == 1);
}
