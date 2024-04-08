import json
import os
import sys
import time
from os.path import join, dirname

import requests
from dotenv import load_dotenv

dotenv_path = join(dirname(__file__), ".env")
load_dotenv(dotenv_path)

LIBRARIES_IO_KEY = os.environ.get("LIBRARIES_IO_KEY")


def get_top_libraries(n):
    # max one query per second
    max_page = n // 100 + 1
    url = "https://libraries.io/api/search?&platforms=Maven&sort=rank&per_page=100"
    libraries = []
    params = {"api_key": LIBRARIES_IO_KEY}
    for page in range(1, max_page + 1):
        params["page"] = page
        response = requests.get(url, params=params)
        libraries.extend(response.json())
        time.sleep(1)  # throttle to max 60 queries per minute

    return libraries[:n]


if __name__ == "__main__":
    if len(sys.argv) != 2 or not sys.argv[1].isdigit():
        print("Usage: python collect_most_popular_pkgs.py <number_of_pkgs>")
        sys.exit(1)

    libraries = get_top_libraries(int(sys.argv[1]))
    with open("libraries.json", "w") as file:
        json.dump(libraries, file, indent=4)
