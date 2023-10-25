import json
import os
import re
import sys

def clean_group_id(text):
    # Remove ANSI escape codes
    text = re.sub(r'\x1B[@-_][0-?]*[ -/]*[@-~]', '', text)
    # Remove additional non-alphanumeric characters and text
    text = re.sub(r'\[INFO\]\s+', '', text)
    return text.strip()


def clean_json_file(file_path, output_path):
    with open(file_path, 'r') as file:
        data = json.load(file)

    if "effectiveDependencies" in data:
        for dep in data["effectiveDependencies"]:
            dep["groupId"] = clean_group_id(dep["groupId"])

    with open(output_path, 'w') as file:
        json.dump(data, file, indent=2)
    print(f"Cleaned and saved to {output_path}")

def main(directory_path):
    if not os.path.exists(directory_path):
        print("The provided directory does not exist.")
        return

    for file_name in os.listdir(directory_path):
        if file_name.endswith(".json"):
            file_path = os.path.join(directory_path, file_name)
            output_path = os.path.join(directory_path, file_name.replace(".json", "_cleaned.json"))
            clean_json_file(file_path, output_path)

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: " + sys.argv[0] + " <directory path>")
    else:
        main(sys.argv[1])