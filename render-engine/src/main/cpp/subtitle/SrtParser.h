#pragma once

#include <cstdint>
#include <string>
#include <vector>

struct SubtitleEntry {
    int64_t startUs;
    int64_t endUs;
    std::string text;
};

class SrtParser {
public:
    static bool parseFile(const std::string& filePath,
                          std::vector<SubtitleEntry>& entries);
    static bool parseString(const std::string& content,
                            std::vector<SubtitleEntry>& entries);

private:
    static int64_t parseTimestamp(const std::string& ts);
};

class SubtitleTrack {
public:
    void load(const std::string& srtPath);
    void clear();
    const std::string& textAt(int64_t timelinePositionUs) const;
    bool isLoaded() const { return loaded_; }
    size_t size() const { return entries_.size(); }

private:
    std::vector<SubtitleEntry> entries_;
    bool loaded_ = false;
    static const std::string kEmpty;
};
