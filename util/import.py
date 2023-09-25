import os
import sys
import json
from zipfile import ZipFile
from pymongo import MongoClient

if len(sys.argv) < 3:
    print("Usage: python script_name.py <path_to_zip_file> <path_to_extract_folder>")
    exit(1)

zip_file_path = sys.argv[1]
extracted_folder = sys.argv[2]

client = MongoClient('localhost', 27017)
db = client["osv_db"]
collection = db["data"]

with ZipFile(zip_file_path, 'r') as zip_ref:
    zip_ref.extractall(extracted_folder)

for json_file in os.listdir(extracted_folder):
    with open(os.path.join(extracted_folder, json_file), "r") as f:
        data = json.load(f)
        collection.insert_one(data)

print("Data loaded to MongoDB successfully!")