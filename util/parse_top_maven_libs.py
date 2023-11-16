from bs4 import BeautifulSoup
import json


def extract_libraries(html_string):
    soup = BeautifulSoup(html_string, "html.parser")
    libraries = []
    for div in soup.find_all("div", class_="im"):
        if div.find("div", class_="im-header") is None:
            # div is an addiv
            continue
        title = (
            div.find("div", class_="im-header")
            .find("h2", class_="im-title")
            .find("a")
            .text.strip()
        )
        subtitle = div.find("div", class_="im-header").find("p", class_="im-subtitle")
        links = subtitle.find_all("a")
        groupId = links[0].text.strip()
        artifactId = links[1].text.strip()
        libraries.append((groupId, artifactId, title))
    return libraries


with open("libraries.json", "r") as file:
    libraries = json.load(file)


def read_html_string():
    lines = []
    while True:
        line = input()
        if line == "END":
            break
        lines.append(line)
    return "\n".join(lines)


if False:
    # it's for extracting the top used libraries
    for _ in range(10):
        print("Enter HTML string (type 'END' on a new line to finish):")
        html_string = read_html_string()
        libraries.extend(extract_libraries(html_string))


def extract_versions(html_string):
    soup = BeautifulSoup(html_string, "html.parser")
    version_data = []

    table = soup.select_one("#snippets .grid.versions")
    if not table:
        return version_data

    for tr in table.find_all("tr"):
        version_link = tr.find("a", class_="vbtn release")
        usages_link = tr.find("div", class_="pbt")
        vulnerabilities_link = tr.find("a", class_="vuln")

        if version_link:
            version = version_link.text.strip()
            usages = (
                int(usages_link.text.strip().replace(",", ""))
                if usages_link and usages_link.text.strip().replace(",", "").isdigit()
                else 0
            )
            vulnerabilities = (
                int(vulnerabilities_link.text.split()[0].replace(",", ""))
                if vulnerabilities_link
                else 0
            )

            version_data.append((version, usages, vulnerabilities))

    return version_data


def find_most_popular_and_vulnerable(version_data):
    non_vulnerable_versions = [v for v in version_data if v[2] == 0]
    vulnerable_versions = [v for v in version_data if v[2] > 0]

    most_popular_non_vulnerable = max(
        non_vulnerable_versions, key=lambda x: x[1], default=None
    )
    most_popular_vulnerable = max(vulnerable_versions, key=lambda x: x[1], default=None)

    return most_popular_non_vulnerable, most_popular_vulnerable


def extract_library_info(html_string):
    soup = BeautifulSoup(html_string, "html.parser")
    breadcrumb = soup.select_one("div.breadcrumb")
    if not breadcrumb:
        return None, None
    breadcrumb_parts = breadcrumb.get_text(strip=True).split("»")
    if len(breadcrumb_parts) < 3:
        return None, None

    groupId = breadcrumb_parts[1].strip()
    artifactId = breadcrumb_parts[2].strip()
    return groupId, artifactId


def update_library_versions(
    libraries, groupId, artifactId, most_popular, most_vulnerable
):
    for lib in libraries:
        if lib["groupId"] == groupId and lib["artifactId"] == artifactId:
            lib["mostPopularVersion"] = most_popular
            lib["mostVulnerableVersion"] = most_vulnerable
            break


for _ in range(1):
    print("Enter HTML string (type 'END' on a new line to finish):")
    html_string = read_html_string()
    if html_string == "KEK\nEND":
        break

    groupId, artifactId = extract_library_info(html_string)

    most_popular, most_vulnerable = find_most_popular_and_vulnerable(
        extract_versions(html_string)
    )

    update_library_versions(
        libraries, groupId, artifactId, most_popular, most_vulnerable
    )


with open("libraries.json", "w") as file:
    json.dump(libraries, file, indent=4)
