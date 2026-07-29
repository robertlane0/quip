CXX      := g++
CXXFLAGS := -std=c++20 -Wall -Wextra -Wpedantic -O2
TARGET   := quip_host
SRC      := quip_linux.cpp

.PHONY: all run clean

all: $(TARGET)

$(TARGET): $(SRC)
	$(CXX) $(CXXFLAGS) -o $@ $<
run:
	./$(TARGET)

clean:
	rm -f $(TARGET)
