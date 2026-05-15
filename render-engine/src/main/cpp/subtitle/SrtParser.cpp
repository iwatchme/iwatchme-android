#include "SrtParser.h"

#include <algorithm>
#include <cstdio>
#include <fstream>
#include <sstream>

#include "common/log.h"

const std::string SubtitleTrack::kEmpty;

namespace {

void trimRight(std::string& s) {
    while (!s.empty() && (s.back() == '\r' || s.back() == ' ' || s.back() == '\t')) {
        s.pop_back();
    }
}

void stripUtf8Bom(std::string& s) {
    if (s.size() >= 3 &&
        static_cast<unsigned char>(s[0]) == 0xEF &&
        static_cast<unsigned char>(s[1]) == 0xBB &&
        static_cast<unsigned char>(s[2]) == 0xBF) {
        s.erase(0, 3);
    }
}

bool isBlank(const std::string& s) {
    for (char c : s) {
        if (c != ' ' && c != '\t' && c != '\r') return false;
    }
    return true;
}

}  // namespace

int64_t SrtParser::parseTimestamp(const std::string& ts) {
    // "HH:MM:SS,mmm"
    int h = 0, m = 0, s = 0, ms = 0;
    if (std::sscanf(ts.c_str(), "%d:%d:%d,%d", &h, &m, &s, &ms) != 4) {
        if (std::sscanf(ts.c_str(), "%d:%d:%d.%d", &h, &m, &s, &ms) != 4) {
            return -1;
        }
    }
    int64_t total = static_cast<int64_t>(h) * 3600 + m * 60 + s;
    return total * 1'000'000LL + static_cast<int64_t>(ms) * 1000LL;
}

bool SrtParser::parseString(const std::string& content,
                            std::vector<SubtitleEntry>& entries) {
    entries.clear();
    std::string buf = content;
    stripUtf8Bom(buf);

    std::istringstream is(buf);
    std::string line;

    enum State { EXPECT_INDEX, EXPECT_TIME, READING_TEXT };
    State state = EXPECT_INDEX;
    SubtitleEntry cur{};
    std::string textAcc;

    auto commit = [&]() {
        if (cur.endUs > cur.startUs && !textAcc.empty()) {
            trimRight(textAcc);
            cur.text = textAcc;
            entries.push_back(cur);
        }
        cur = SubtitleEntry{};
        textAcc.clear();
    };

    while (std::getline(is, line)) {
        trimRight(line);

        switch (state) {
            case EXPECT_INDEX: {
                if (isBlank(line)) break;
                state = EXPECT_TIME;
                break;
            }
            case EXPECT_TIME: {
                auto arrow = line.find("-->");
                if (arrow == std::string::npos) {
                    state = EXPECT_INDEX;
                    break;
                }
                std::string startStr = line.substr(0, arrow);
                std::string endStr = line.substr(arrow + 3);
                while (!startStr.empty() && (startStr.back() == ' ' || startStr.back() == '\t')) startStr.pop_back();
                size_t s2 = endStr.find_first_not_of(" \t");
                if (s2 != std::string::npos) endStr.erase(0, s2);
                int64_t a = parseTimestamp(startStr);
                int64_t b = parseTimestamp(endStr);
                if (a < 0 || b < 0) {
                    state = EXPECT_INDEX;
                    break;
                }
                cur.startUs = a;
                cur.endUs = b;
                state = READING_TEXT;
                break;
            }
            case READING_TEXT: {
                if (isBlank(line)) {
                    commit();
                    state = EXPECT_INDEX;
                } else {
                    if (!textAcc.empty()) textAcc.push_back('\n');
                    textAcc += line;
                }
                break;
            }
        }
    }
    if (state == READING_TEXT) commit();

    std::sort(entries.begin(), entries.end(),
              [](const SubtitleEntry& a, const SubtitleEntry& b) {
                  return a.startUs < b.startUs;
              });
    return true;
}

bool SrtParser::parseFile(const std::string& filePath,
                          std::vector<SubtitleEntry>& entries) {
    std::ifstream f(filePath, std::ios::binary);
    if (!f.is_open()) {
        LOGE("SrtParser: cannot open %s", filePath.c_str());
        return false;
    }
    std::stringstream buf;
    buf << f.rdbuf();
    return parseString(buf.str(), entries);
}

void SubtitleTrack::load(const std::string& srtPath) {
    entries_.clear();
    loaded_ = SrtParser::parseFile(srtPath, entries_);
    LOGI("SubtitleTrack: loaded %zu entries from %s", entries_.size(), srtPath.c_str());
}

void SubtitleTrack::clear() {
    entries_.clear();
    loaded_ = false;
}

const std::string& SubtitleTrack::textAt(int64_t timelinePositionUs) const {
    if (entries_.empty()) return kEmpty;
    auto it = std::upper_bound(entries_.begin(), entries_.end(), timelinePositionUs,
                               [](int64_t pos, const SubtitleEntry& e) {
                                   return pos < e.startUs;
                               });
    if (it == entries_.begin()) return kEmpty;
    --it;
    if (timelinePositionUs >= it->startUs && timelinePositionUs < it->endUs) {
        return it->text;
    }
    return kEmpty;
}
