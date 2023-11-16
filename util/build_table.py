import argparse
import json
import os


def generate_latex_table(folder_path):
    folder_path = os.path.abspath(folder_path)
    thresholds = [0.5, 0.75, 0.9, 0.95, 0.99, 1.0]
    latex_table = []

    latex_table.append(r"\begin{tabular}{ccccccc}")
    latex_table.append(r"\toprule")
    latex_table.append(r"Configuration & Threshold & Precision & Recall & F1 Score \\")
    latex_table.append(r"\midrule")

    for configuration in ["Relocation Disabled", "Relocation Enabled", "Minimize Jar Disabled", "Minimize Jar Enabled"]:
        latex_table.append(f"{configuration} & & & & \\\\")
        for threshold in thresholds:
            filename = os.path.join(folder_path, f"stats_{threshold}.json")
            if not os.path.exists(filename):
                print(f"File not found: {filename}")
                continue

            with open(filename, "r") as f:
                data = json.load(f)
                suffix = configuration.replace(" ", "")

                total_f1_score = data[f"totalF1Score{suffix}"]
                precision = data[f"precision{suffix}"]
                recall = data[f"recall{suffix}"]
                total_projects = data[f"totalProjects{suffix}"]

                if total_projects == 0:
                    precision_val = recall_val = f1_score_val = 0
                else:
                    precision_val = precision / total_projects
                    recall_val = recall / total_projects
                    f1_score_val = total_f1_score / total_projects

                row = f"& {threshold} & {precision_val:.3f} & {recall_val:.3f} & {f1_score_val:.3f} \\\\"
                latex_table.append(row)
        latex_table.append(r"\midrule")

    latex_table.append(r"\bottomrule")
    latex_table.append(r"\end{tabular}")

    latex_code = "\n".join(latex_table)
    print(latex_code)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Generate LaTeX table from JSON files."
    )
    parser.add_argument(
        "folder_path", type=str, help="Path to the folder containing the JSON files"
    )
    args = parser.parse_args()
    generate_latex_table(args.folder_path)
