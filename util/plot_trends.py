import argparse
import json

import matplotlib.pyplot as plt
import pandas as pd


def read_json(file_path):
    with open(file_path, "r") as f:
        return json.load(f)


def calculate_percentages(shade_data, general_data):
    percentages = {category: {} for category in shade_data.keys()}
    for category, trends in shade_data.items():
        for date, count in trends.items():
            total_count = general_data.get(date, 0)
            if total_count > 0:
                percentages[category][date] = (count / total_count) * 100
    return percentages


def plot_percentages(data, save_plot):
    plt.rcParams.update(
        {
            "font.family": "serif",
            "text.usetex": True,
            "pgf.rcfonts": False,
            "pgf.preamble": r"\usepackage{times}" + "\n" + r"\usepackage{mathptmx}",
        }
    )

    fig, ax = plt.subplots(figsize=(10, 5))

    for category, trends in data.items():
        df = pd.DataFrame(list(trends.items()), columns=["Date", category])
        df["Date"] = pd.to_datetime(df["Date"])
        df = df.sort_values(by="Date")
        ax.plot(df["Date"], df[category], marker="o", label=category)

    ax.set_xlabel("Date")
    ax.set_ylabel("Percentage")
    ax.set_title("Percentage of Each Shade-Plugin Category Over Time")
    ax.legend()
    ax.grid(True)
    plt.tight_layout()

    if save_plot:
        plt.savefig("percentages_plot.pdf", bbox_inches="tight")
        print("Plot saved to 'percentages_plot.pdf'")
    else:
        plt.show()

    plt.close()


def main():
    parser = argparse.ArgumentParser(
        description="Plot trends and percentages over time from JSON data."
    )
    parser.add_argument("file_path", type=str, help="Path to the JSON data file")
    parser.add_argument(
        "--save", action="store_true", help="Save the plot instead of showing it"
    )
    args = parser.parse_args()

    data = read_json(args.file_path)
    shade_data = data.get("shade_plugin_trends", {})
    general_data = data.get("general_trends", {})

    percentages = calculate_percentages(shade_data, general_data)
    plot_percentages(percentages, args.save)


if __name__ == "__main__":
    main()
