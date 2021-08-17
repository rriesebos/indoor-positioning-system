import numpy as np
import matplotlib.pyplot as plt
import pandas as pd


BIN_SIZE = 60    # Bin width/height in cm (bins are square)
MIN_COUNT = 0    # Minimal count for a bin to be visible


def plot_heatmap(trace_filename):
    df = pd.read_json(trace_filename)

    # Filter out impossible coordinates based on the floor plan
    df = df[((df.x > 700) & ((df.x < 1140) | (df.y > 660))) | ((df.x < 700) & (df.y < 620) & (df.y > 220))]

    x_boundary = 1200
    y_boundary = 960

    bbox = (0, x_boundary, 0, y_boundary)
    x_range = [bbox[0], bbox[1]]
    y_range = [bbox[2], bbox[3]]

    floor_plan = np.flip(plt.imread('living-room-floor-plan.png'))
    plt.xlim(x_range)
    plt.ylim(y_range)
    plt.imshow(floor_plan, extent=bbox)

    plt.title('Heatmap of the predicted coordinates')
    plt.hist2d(df.x, df.y, cmap='hot', range=[x_range, y_range], cmin=MIN_COUNT,
               bins=[x_boundary // BIN_SIZE, y_boundary // BIN_SIZE], alpha=0.5)
    plt.colorbar()
    plt.show()


def plot_all_heatmaps(trace_filenames, confidence_filter=False):
    x_coordinates, y_coordinates, confidence_indicators = [], [], []
    for trace_filename in trace_filenames:
        df = pd.read_json(trace_filename)

        # Filter out impossible coordinates based on the floor plan
        df = df[((df.x > 700) & ((df.x < 1140) | (df.y > 660))) | ((df.x < 700) & (df.y < 620) & (df.y > 220))]

        x_coordinates.extend(df.x.to_list())
        y_coordinates.extend(df.y.to_list())
        confidence_indicators.extend(df.confidence.to_list())

    if confidence_filter:
        confidence_threshold = np.average(confidence_indicators)
        print(confidence_threshold)
        x_coordinates = [coords for i, coords in enumerate(x_coordinates)
                         if confidence_indicators[i] > confidence_threshold]
        y_coordinates = [coords for i, coords in enumerate(y_coordinates)
                         if confidence_indicators[i] > confidence_threshold]

    x_boundary = 1200
    y_boundary = 960

    bbox = (0, x_boundary, 0, y_boundary)
    x_range = [bbox[0], bbox[1]]
    y_range = [bbox[2], bbox[3]]

    floor_plan = np.flip(plt.imread('living-room-floor-plan.png'))
    plt.xlim(x_range)
    plt.ylim(y_range)
    plt.imshow(floor_plan, extent=bbox)

    plt.title('Heatmap of the predicted coordinates')
    plt.hist2d(x_coordinates, y_coordinates, cmap='hot', range=[x_range, y_range], cmin=MIN_COUNT,
               bins=[x_boundary // BIN_SIZE, y_boundary // BIN_SIZE], alpha=0.5)
    plt.colorbar()
    plt.show()


def main():
    # plot_heatmap('traces/trace1/trace.json')

    base_traces = [f'traces/trace{i}/trace.json' for i in range(1, 11)]
    plot_all_heatmaps(base_traces)

    best_traces = [f'traces/trace{i}/replayed/windowSize=10_distanceMethod=MEDIAN_distanceModel=' \
                   f'FITTED_AVERAGE_pathLossExponent=-1.0_positioningMethod=WEIGHTED_CENTROID_weightExponent' \
                   f'=1.5_pdfSharpness=-1.0.json' for i in range(1, 11)]
    plot_all_heatmaps(best_traces)
    plot_all_heatmaps(best_traces, confidence_filter=True)


if __name__ == '__main__':
    main()
