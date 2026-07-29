CXX      := g++
CXXFLAGS := -std=c++20 -Wall -Wextra -Wpedantic -O2
TARGET   := quip_host
SRC      := quip_linux.cpp

.PHONY: all clean

all: $(TARGET)

$(TARGET): $(SRC)
	$(CXX) $(CXXFLAGS) -o $@ $<

clean:
	rm -f $(TARGET)
