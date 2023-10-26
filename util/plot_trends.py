import matplotlib.pyplot as plt
import pandas as pd
import json
import argparse

def read_json(file_path):
    with open(file_path, "r") as f:
        return json.load(f)


def plot_trends(data, save_plot):
    plt.rcParams.update({
        "font.family": "serif",
        "text.usetex": True,
        "pgf.rcfonts": False,
        "pgf.preamble": r"\usepackage{times}" + "\n" + r"\usepackage{mathptmx}",
    })

    fig, ax = plt.subplots(figsize=(10, 5))

    for trend_name, trend_data in data["shade_plugin_trends"].items():
        if trend_data:
            df = pd.DataFrame(list(trend_data.items()), columns=["Date", "Count"])
            df["Date"] = pd.to_datetime(df["Date"])
            df = df.sort_values(by="Date")
            ax.plot(df["Date"], df["Count"], marker="o", label=trend_name)

    ax.set_xlabel("Date")
    ax.set_ylabel("Count")
    ax.set_title("Trend Over Time")
    ax.legend()
    ax.grid(True)
    plt.tight_layout()

    if save_plot:
        plt.savefig("trends_plot.pdf", bbox_inches="tight")
        print("Plot saved to 'trends_plot.pdf'")
    else:
        plt.show()

    plt.close()


def main():
    parser = argparse.ArgumentParser(
        description="Plot trends over time from JSON data."
    )
    parser.add_argument("file_path", type=str, help="Path to the JSON data file")
    parser.add_argument("--save", action="store_true", help="Save the plot instead of showing it")
    args = parser.parse_args()

    data = read_json(args.file_path)
    plot_trends(data, args.save)


if __name__ == "__main__":
    main()
