#!/bin/bash

# Check arguments
if [[ ($# -ne 2 && $# -ne 4) || ($# == 4 && "$3" != "-s") ]]; then
    echo "Usage: $0 <unzipped_jar_dir1> <unzipped_jar_dir2>"
    echo "Or: $0 <unzipped_jar_dir1> -s <prefix> <unzipped_jar_dir2>"
    exit 1
fi
echo "Number of arguments: $#"
echo "$0"
echo "$1"
echo "$2"
echo "$3"
echo "$4"

dir1="$1"
if [[ "$3" == "-s" ]]; then
    prefix="$4"
    dir2="$2/${prefix}"
else
    dir2="$2"
fi


echo "Directory 1: $dir1"
echo "Directory 2: $dir2"

execute_command() {
    echo "Executing: $@"
    # "$@"
}

# Function to compute SHA-256 hash
compute_sha256() {
    echo $(sha256sum "$1" | cut -d ' ' -f 1)
}

compute_md5() {
    echo $(md5sum "$1" | cut -d ' ' -f 1)
}

compute_crc32() {
    echo $(crc32 "$1")
}

# Compare using SHA-256 and javap
for class_file in $(find "$dir1" -name "*.class"); do
    relative_path=${class_file#$dir1/}
    class2_file="$dir2/$relative_path"

    if [[ -f "$class2_file" ]]; then
        sha256_1=$(compute_sha256 "$class_file")
        sha256_2=$(compute_sha256 "$class2_file")

        md5_1=$(compute_md5 "$class_file")
        md5_2=$(compute_md5 "$class2_file")

        crc32_1=$(compute_crc32 "$class_file")
        crc32_2=$(compute_crc32 "$class2_file")

        if [[ "$sha256_1" != "$sha256_2" ]]; then
            echo "SHA-256 difference in $relative_path:"
            echo "Directory 1: $sha256_1"
            echo "Directory 2: $sha256_2"

            echo "MD5 difference in $relative_path:"
            echo "Directory 1: $md5_1"
            echo "Directory 2: $md5_2"

            echo "CRC32 difference in $relative_path:"
            echo "Directory 1: $crc32_1"
            echo "Directory 2: $crc32_2"
            
            # If you still want to see the bytecode difference, you can include the javap comparison here
            output1=$(execute_command javap -v "$class_file")
            output2=$(execute_command javap -v "$class2_file")
            diff <(echo "$output1") <(echo "$output2")
        fi
    else
        echo "$relative_path is missing in the second directory"
    fi
done
