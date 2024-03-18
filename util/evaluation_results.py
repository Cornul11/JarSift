import json
import os

project_dir = "../projects_metadata"
result_dir = "../evaluation"
project_files = os.listdir(project_dir)


def compare_results(expected, actual, threshold=0.95):
    true_positives = 0
    false_positives = 0
    false_negatives = 0

    for dep in expected["effectiveDependencies"]:
        if not dep["presentInDatabase"]:
            continue  # skip dependencies that are not present in the database

        if dep in actual["notFoundLibraries"]:
            # not one class file of this dep was found in the uber-jar, then it most probably has no class files
            continue

        found = False
        gav = dep["groupId"] + ":" + dep["artifactId"] + ":" + dep["version"]

        for inferred_dep in actual["inferredLibraries"]:
            if inferred_dep["includedRatio"] < threshold:
                continue
            if gav == inferred_dep["gav"] or gav in inferred_dep["alternativeVersions"]:
                true_positives += 1
                found = True
                break

        if not found:
            false_negatives += 1

    nb_actual = sum(
        1 for inferred_dep in actual["inferredLibraries"] if inferred_dep["includedRatio"] >= threshold
    )
    false_positives = nb_actual - true_positives
    return (true_positives, false_positives, false_negatives)


shadeConfigurations = [(True, True), (True, False), (False, True), (False, False)]
for shadeConfig in shadeConfigurations:
    precisions = {}
    recalls = {}
    f1s = {}
    for threshold in [0.5, 0.75, 0.9, 0.95, 0.99, 1.0]:
        precisions[threshold] = []
        recalls[threshold] = []
        f1s[threshold] = []
        for project_file in sorted(project_files):
            expected_data = None
            actual_data = None
            with open(os.path.join(project_dir, project_file), "r") as f:
                expected_data = json.load(f)

                if (
                    expected_data["shadeConfiguration"]["minimizeJar"]
                    and not shadeConfig[0]
                ):
                    continue
                if (
                    expected_data["shadeConfiguration"]["relocation"]
                    and not shadeConfig[1]
                ):
                    continue
            actual_file_path = os.path.join(
                result_dir, project_file.replace(".json", "_libraries.json")
            )
            if not os.path.exists(actual_file_path):
                continue
            with open(actual_file_path, "r") as f:
                actual_data = json.load(f)

            results = compare_results(expected_data, actual_data, threshold)

            precision = (
                results[0] / (results[0] + results[1])
                if results[0] + results[1] > 0
                else 1
            )
            recall = (
                results[0] / (results[0] + results[2])
                if results[0] + results[2] > 0
                else 1
            )
            f1 = (
                2 * precision * recall / (precision + recall)
                if precision + recall > 0
                else 0
            )

            precisions[threshold].append(precision)
            recalls[threshold].append(recall)
            f1s[threshold].append(f1)
            # print(pfile, results, precision, recall, f1)

    print("minimizeJar:", shadeConfig[0], "relocation:", shadeConfig[1])
    for threshold in [0.25, 0.5, 0.75, 0.9, 0.95, 0.99, 1.0]:
        precision = sum(precisions[threshold]) / len(precisions[threshold])
        recall = sum(recalls[threshold]) / len(recalls[threshold])
        f1 = sum(f1s[threshold]) / len(f1s[threshold])
        print("%.2f & %.3f & %.3f & %.3f \\\\" % (threshold, precision, recall, f1))
    print("")
