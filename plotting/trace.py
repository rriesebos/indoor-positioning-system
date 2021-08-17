import numpy as np
import matplotlib.pyplot as plt
import matplotlib.patheffects as pe
from matplotlib.lines import Line2D
import pandas as pd


def read_json(filename):
    df = pd.read_json(filename)
    df = df.rename({'system.totimestamp(timeuuid)': 'time'}, axis='columns')
    df = df.sort_values(by=['time'], ignore_index=True)

    return df


def plot_floor_plan():
    bbox = (0, 1200, 0, 960)
    floor_plan = np.flip(plt.imread('living-room-floor-plan.png'))

    plt.title('Trace plotted on floor plan')
    plt.xlabel("x (cm)")
    plt.ylabel("y (cm)")
    plt.xlim(bbox[0], bbox[1])
    plt.ylim(bbox[2], bbox[3])
    plt.imshow(floor_plan, extent=bbox)


def plot_beacons(show_labels=True):
    beacon_coordinates = [(25, 438), (230, 608), (390, 283), (575, 283), (600, 593),
                          (820, 598), (920, 78), (1120, 298), (1100, 580), (740, 320)]
    beacon_labels = ['BR532317', 'BR532396', 'BR532386', 'BR532394', 'BR532388',
                     'BR532389', 'BR532401', 'BR532390', 'BR532391', 'BR532392']
    beacon_label_offsets = [(-5, 11), (0, 11), (-10, -17), (10, -17), (-5, 11),
                            (-5, 11), (0, -17), (35, 0), (33, 0), (15, 11)]
    beacons_x, beacons_y = zip(*beacon_coordinates)

    plt.scatter(beacons_x, beacons_y, c='tab:green', label='Beacon')

    if show_labels:
        bbox_props = dict(boxstyle="round", facecolor="w", edgecolor="k", alpha=0.8)
        for i, label in enumerate(beacon_labels):
            plt.annotate(label, (beacons_x[i], beacons_y[i]), textcoords="offset points",
                         xytext=beacon_label_offsets[i], horizontalalignment='center',
                         bbox=bbox_props, fontsize=9)


def plot_trace(trace_x, trace_y, plot_type='scatter', label='Trace', annotate=False):
    if plot_type == 'scatter' or plot_type == 'both':
        plt.scatter(trace_x, trace_y, c='tab:orange', alpha=0.8, label=label, zorder=2)

        if annotate:
            for i in range(len(trace_x)):
                plt.annotate(i, (trace_x[i], trace_y[i]), horizontalalignment='center', fontsize=9,
                             path_effects=[pe.withStroke(linewidth=2, foreground="white")])

    if plot_type == 'line' or plot_type == 'both':
        plt.plot(trace_x, trace_y, c='tab:orange', alpha=0.8, label=label, linewidth=2, zorder=2)


def plot_ground_truth(ground_truth):
    ground_truth_x, ground_truth_y = zip(*ground_truth)

    plt.scatter(ground_truth_x, ground_truth_y, c='black', alpha=0.8, zorder=2, label='Checkpoint')
    for i in range(len(ground_truth_x)):
        plt.annotate(i + 1, (ground_truth_x[i], ground_truth_y[i]), textcoords="offset points",
                     xytext=(5, 3), horizontalalignment='center', fontsize=9)

    plt.plot(ground_truth_x, ground_truth_y, c='black', alpha=0.8, zorder=1)


def plot_interpolated_ground_truth(trace_path, checkpoints_path, ground_truth):
    plot_floor_plan()
    plot_beacons()
    plot_ground_truth(ground_truth)

    checkpoints = read_json(checkpoints_path)
    trace = read_json(trace_path)
    ground_truth_interpolated = interpolate_ground_truth(checkpoints, trace, ground_truth)

    plt.title('Interpolated ground truth')
    plt.scatter(*zip(*ground_truth_interpolated), label="Interpolated checkpoint", zorder=2)
    plt.show()


def plot(trace_path=None, checkpoints_path=None, ground_truth=None, show_ground_truth=True,
         show_ground_truth_interpolated=False, show_error_lines=True, window=1, filter_type='mean',
         plot_type='scatter', show_beacons=True, show_beacon_labels=True, filter_by_confidence=False, show=True):
    plot_floor_plan()

    if ground_truth and show_ground_truth:
        plot_ground_truth(ground_truth)

    if trace_path:
        df_trace = read_json(trace_path)
        df_checkpoints = None

        if checkpoints_path:
            # Filter trace to only include values between first and last checkpoint
            df_checkpoints = read_json(checkpoints_path)
            start_time, stop_time = df_checkpoints.time.iloc[0], df_checkpoints.time.iloc[-1]
            df_trace = df_trace[(df_trace.time >= start_time) & (df_trace.time <= stop_time)]
            df_trace = df_trace.reset_index(drop=True)

            if show_ground_truth_interpolated:
                ground_truth_interpolated = list(interpolate_ground_truth(df_checkpoints, df_trace, ground_truth))
                plt.scatter(*zip(*ground_truth_interpolated), c='tab:blue', label="Interpolated checkpoint",
                            alpha=0.8, zorder=1.5)

                if show_error_lines:
                    for i, coords in enumerate(list(zip(df_trace.x, df_trace.y))):
                        plt.plot([coords[0], ground_truth_interpolated[i][0]],
                                 [coords[1], ground_truth_interpolated[i][1]],
                                 color='red', alpha=0.8, zorder=0.5)

        if filter_by_confidence:
            confidence_threshold = np.mean(df_trace.confidence) - np.std(df_trace.confidence)
            df_trace_filtered = df_trace[(df_trace.confidence <= confidence_threshold)]
            plt.scatter(df_trace_filtered.x, df_trace_filtered.y, marker='x', c='tab:red', zorder=3)

        trace = df_trace.copy()
        if window > 1:
            if filter_type == 'mean':
                trace.x = df_trace.x.rolling(window).mean()
                trace.y = df_trace.y.rolling(window).mean()

            if filter_type == 'median':
                trace.x = df_trace.x.rolling(window).median()
                trace.y = df_trace.y.rolling(window).median()

            trace = trace[window:].reset_index(drop=True)
            plot_trace(trace.x, trace.y, label=f'Trace {filter_type}', plot_type=plot_type)
        else:
            plot_trace(df_trace.x, df_trace.y, plot_type=plot_type)

        if df_checkpoints is not None and ground_truth:
            calculate_errors(checkpoints_path, trace_path, ground_truth)

    if show_beacons:
        plot_beacons(show_beacon_labels)

    plt.title('Trace compared to the interpolated checkpoints')

    if show:
        plt.legend(loc='upper left')
        plt.show()
        # plt.savefig('trace-errors.pdf', bbox_inches="tight")


def interpolate(checkpoint_1, checkpoint_2, start_time, stop_time, time):
    start_timestamp = pd.to_datetime(start_time).timestamp()
    stop_timestamp = pd.to_datetime(stop_time).timestamp()
    timestamp = pd.to_datetime(time).timestamp()

    dx = checkpoint_2[0] - checkpoint_1[0]
    dy = checkpoint_2[1] - checkpoint_1[1]
    dt = (timestamp - start_timestamp) / (stop_timestamp - start_timestamp)

    # Interpolating assumes that the walking speed is constant
    return dt * dx + checkpoint_1[0], dt * dy + checkpoint_1[1]


def interpolate_ground_truth(df_checkpoints, df_trace, ground_truth):
    ground_truth_interpolated = []
    for i in range(0, len(df_checkpoints) - 1):
        # Get the coordinates and timestamps for each pair of checkpoints
        checkpoint_1, checkpoint_2 = ground_truth[i], ground_truth[i + 1]
        start_time, stop_time = df_checkpoints.time[i], df_checkpoints.time[i + 1]

        # Loop over the coordinates that fall between each pair of checkpoints and add the interpolated ground truths
        between_checkpoints = df_trace[(df_trace.time >= start_time) & (df_trace.time <= stop_time)]
        for index, row in between_checkpoints.iterrows():
            interpolated_coordinates = interpolate(checkpoint_1, checkpoint_2, start_time, stop_time, row.time)
            ground_truth_interpolated.append(interpolated_coordinates)

    return ground_truth_interpolated


def calculate_distance(first_coordinates, second_coordinates):
    return np.linalg.norm(np.subtract(first_coordinates, second_coordinates)) / 100


def calculate_errors(checkpoints_path, trace_path, ground_truth):
    df_trace = read_json(trace_path)
    df_checkpoints = read_json(checkpoints_path)

    trace = list(zip(df_trace.x, df_trace.y))
    ground_truth_interpolated = interpolate_ground_truth(df_checkpoints, df_trace, ground_truth)

    mean_error = calculate_mean_error(trace, ground_truth_interpolated)
    root_mean_squared_error = calculate_root_mean_square_error(trace, ground_truth_interpolated)
    median_error = calculate_median_error(trace, ground_truth_interpolated)
    percentile_75th_error = calculate_percentile_error(trace, ground_truth_interpolated, 75)
    percentile_90_error = calculate_percentile_error(trace, ground_truth_interpolated, 90)

    print("Distance/positioning error")
    print(f'Mean: {mean_error}')
    print(f'Root mean squared: {root_mean_squared_error}')
    print(f'Median: {median_error}')
    print(f'75th percentile: {percentile_75th_error}')
    print(f'90th percentile: {percentile_90_error}')
    print()


def calculate_mean_error(trace, ground_truth):
    """Calculate the mean error of the trace wrt the ground truth

    Args:
        trace: List of coordinates representing the trace generated by the positioning system
        ground_truth: The ground truth list of coordinates (tuples) in centimeters

    Returns:
        Returns the sum of the Euclidean distances between the ground truth and trace in meters

    """

    errors = list(map(calculate_distance, ground_truth, trace))
    return np.mean(errors)


def calculate_root_mean_square_error(trace, ground_truth):
    errors = list(map(calculate_distance, ground_truth, trace))
    return np.sqrt(np.mean(np.array(errors) ** 2))


def calculate_median_error(trace, ground_truth):
    errors = list(map(calculate_distance, ground_truth, trace))
    return np.median(errors)


def calculate_percentile_error(trace, ground_truth, percentile):
    errors = list(map(calculate_distance, ground_truth, trace))
    return np.percentile(errors, percentile)


def evaluate_simulated_traces(base_path, checkpoints_path, ground_truth, filter_by_confidence=False):
    import os
    import re

    directory_encoded = os.fsencode(base_path)
    files = os.listdir(directory_encoded)
    files.sort(key=lambda f: int(re.sub(r'\D', '', os.fsdecode(f))))

    trace_errors = []
    for file in files:
        filename = os.fsdecode(file)
        if filename.endswith(".json"):
            df_trace = pd.read_json(f'{base_path}/{filename}')

            if filter_by_confidence:
                confidence_threshold = np.mean(df_trace.confidence) - np.std(df_trace.confidence)
                df_trace = df_trace[(df_trace.confidence > confidence_threshold)]

            trace = list(zip(df_trace.x, df_trace.y))

            # If only a single ground truth coordinate is supplied, use it as a static evaluation
            if isinstance(ground_truth, tuple) or len(ground_truth) == 1:
                ground_truth_interpolated = [ground_truth for _ in range(len(trace))]
            else:
                df_checkpoints = read_json(checkpoints_path)
                ground_truth_interpolated = interpolate_ground_truth(df_checkpoints, df_trace, ground_truth)

            mean_error = calculate_mean_error(trace, ground_truth_interpolated)
            rms_error = calculate_root_mean_square_error(trace, ground_truth_interpolated)
            median_error = calculate_median_error(trace, ground_truth_interpolated)
            percentile_75th_error = calculate_percentile_error(trace, ground_truth_interpolated, 75)
            percentile_90th_error = calculate_percentile_error(trace, ground_truth_interpolated, 90)

            trace_error = {
                'filename': filename,
                'mean_error': mean_error,
                'rms_error': rms_error,
                'median_error': median_error,
                'percentile_75th_error': percentile_75th_error,
                'percentile_90th_error': percentile_90th_error
            }

            trace_errors.append(trace_error)

    return trace_errors


def evaluate_all_traces(ground_truth, filter_by_confidence=False):
    import json

    traces_base_path = 'traces'
    trace_filenames = [f'trace{i}' for i in range(1, 11)]
    for trace_filename in trace_filenames:
        print(trace_filename)
        trace_parameters_errors = evaluate_simulated_traces(f'{traces_base_path}/{trace_filename}/replayed',
                                                            f'{traces_base_path}/{trace_filename}/checkpoints.json',
                                                            ground_truth, filter_by_confidence)

        trace_parameters_errors.sort(key=lambda x: x['mean_error'])

        save_base_path = 'traces/results'
        if filter_by_confidence:
            save_base_path = 'traces/results/filtered'

        with open(f'{save_base_path}/{trace_filename}.json', 'w') as file:
            json.dump(trace_parameters_errors, file)


def plot_confidence_histogram(trace_filename):
    confidences = []
    for i in range(1, 11):
        trace = read_json(f'traces/trace{i}/replayed/{trace_filename}')
        confidences.extend(trace.confidence)

    print(np.average(confidences) - np.std(confidences))

    binwidth = 0.001
    plt.hist(confidences, bins=np.arange(min(confidences), max(confidences) + binwidth, binwidth))
    plt.xticks(np.arange(min(confidences), max(confidences) + binwidth, 5 * binwidth), rotation=60)
    plt.xlabel('Confidence indicator value')
    plt.ylabel('Count')
    plt.title('Histogram of the confidence indicator values of all 10 traces')

    confidence_threshold = np.mean(confidences) - np.std(confidences)
    plt.axvline(confidence_threshold, color='red', linestyle='dashed', linewidth=2)

    plt.annotate(f'Threshold = {confidence_threshold:.3f}', (confidence_threshold, 12),
                 textcoords="offset points", xytext=(-60, 0), horizontalalignment='center', fontsize=10)

    plt.show()
    # plt.savefig('confidence-histogram.pdf', bbox_inches="tight")
    # plt.clf()


def plot_all_traces(trace_filename, ground_truth, filter_by_confidence=False):
    for i in range(1, 11):
        if i == 1:
            plot(f'traces/trace{i}/replayed/{trace_filename}',
                 f'traces/trace{i}/checkpoints.json', ground_truth,
                 show_ground_truth_interpolated=True, show_beacons=True, show_beacon_labels=False,
                 filter_by_confidence=filter_by_confidence, show_error_lines=False, show=False)
        else:
            plot(f'traces/trace{i}/replayed/{trace_filename}',
                 f'traces/trace{i}/checkpoints.json', ground_truth, show_ground_truth=False,
                 show_ground_truth_interpolated=True, show_beacons=False, show_beacon_labels=False,
                 filter_by_confidence=filter_by_confidence, show_error_lines=False, show=False)

    if filter_by_confidence:
        plt.title('All filtered traces plotted on the floor plan')
    else:
        plt.title('All traces plotted on the floor plan')

    legend_elements = [Line2D([0], [0], label='Checkpoint', marker='o', color='w',
                              markerfacecolor='black', markersize=8),
                       Line2D([0], [0], label='Interpolated checkpoint', marker='o', color='w',
                              markerfacecolor='tab:blue', markersize=8),
                       Line2D([0], [0], label='Trace', marker='o', color='w',
                              markerfacecolor='tab:orange', markersize=8),
                       Line2D([0], [0], label='Beacon', marker='o', color='w',
                              markerfacecolor='tab:green', markersize=8)]

    plt.legend(handles=legend_elements, loc='upper left')
    plt.show()
    # plt.savefig('all-traces-probability-filtered.pdf', bbox_inches="tight")


def main():
    ground_truth = [(180, 430), (390, 340), (575, 340),
                    (750, 430), (880, 480), (1080, 480),
                    (1080, 300), (870, 300), (870, 120)]

    trace_filename = 'windowSize=10_distanceMethod=MEDIAN_distanceModel=FITTED_LOS_pathLossExponent=-1.0' \
                     '_positioningMethod=PROBABILITY_weightExponent=-1.0_pdfSharpness=3.5.json'

    plot_confidence_histogram(trace_filename)

    # plot_all_traces(trace_filename, ground_truth)
    plot_all_traces(trace_filename, ground_truth, filter_by_confidence=True)

    # plot(f'traces/trace10/replayed/{trace_filename}', f'traces/trace10/checkpoints.json', ground_truth,
    #      show_ground_truth_interpolated=True, show_beacons=True, show_beacon_labels=False,
    #      filter_by_confidence=False, show_error_lines=True)

    # evaluate_all_traces(ground_truth, filter_by_confidence=False)


if __name__ == "__main__":
    main()
