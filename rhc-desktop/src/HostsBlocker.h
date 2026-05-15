#pragma once
#include <vector>
#include <string>

namespace RHC {
    namespace HostsBlocker {
        void SyncHostsFile(const std::vector<std::string>& blockedDomains);
    }
}
