all:
	mkdir -p bin
	javac -d bin src/vcdiff/*.java

test: all
	mkdir -p tmp
	rm -f tmp/test-*.out
	java -cp bin vcdiff.Main decode testdata/test-1-dictionary testdata/test-1-delta tmp/test-1.out
	java -cp bin vcdiff.Main decode testdata/test-2-dictionary testdata/test-2-delta tmp/test-2.out
	diff tmp/test-1.out testdata/test-1-target
	diff tmp/test-2.out testdata/test-2-target

clean:
	rm -f tmp/*
	rm -f bin/vcdiff/*.class


.PHONY: all test
