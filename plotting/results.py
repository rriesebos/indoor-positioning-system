import json
import re
import math
import matplotlib.pyplot as plt
import itertools
import numpy as np


# Filtered base path: 'traces/results/filtered'
RESULTS_BASE_PATH = 'traces/results'


def split_parameters(filename):
    # Remove .json extension
    parameters_string = filename[:-len('.json')]

    # Split on underscores except if they are followed by 'L', 'N', 'A' or 'C' (LOS, NLOS, AVERAGE, CENTROID)
    parameters = re.split(r'_(?![LNAC])', parameters_string)

    # Split by '=' and convert to dictionary
    return dict([parameter_value.split('=') for parameter_value in parameters])


def aggregate_results(sort_by='all'):
    parameters_errors = {}

    trace_filenames = [f'trace{i}' for i in range(1, 11)]
    nr_of_traces = len(trace_filenames)
    for trace_filename in trace_filenames:
        with open(f'{RESULTS_BASE_PATH}/{trace_filename}.json', 'r') as file:
            results = json.load(file)

        for result in results:
            parameters = result['filename']

            if parameters not in parameters_errors:
                errors = {
                    'mean_error': result['mean_error'] / nr_of_traces,
                    'rms_error': result['rms_error'] / nr_of_traces,
                    'median_error': result['median_error'] / nr_of_traces,
                    'percentile_75th_error': result['percentile_75th_error'] / nr_of_traces,
                    'percentile_90th_error': result['percentile_90th_error'] / nr_of_traces
                }
            else:
                errors = parameters_errors[parameters]

                errors['mean_error'] += result['mean_error'] / nr_of_traces
                errors['rms_error'] += result['rms_error'] / nr_of_traces
                errors['median_error'] += result['median_error'] / nr_of_traces
                errors['percentile_75th_error'] += result['percentile_75th_error'] / nr_of_traces
                errors['percentile_90th_error'] += result['percentile_90th_error'] / nr_of_traces

            parameters_errors[parameters] = errors

    if sort_by == 'all' or sort_by is None:
        parameters_errors = dict(sorted(parameters_errors.items(),
                                        key=lambda x: np.average([x[1]['mean_error'],
                                                                  x[1]['rms_error'],
                                                                  x[1]['median_error'],
                                                                  x[1]['percentile_75th_error'],
                                                                  x[1]['percentile_90th_error']])))
    else:
        parameters_errors = dict(sorted(parameters_errors.items(), key=lambda x: x[1][sort_by]))

    return parameters_errors


def calculate_standard_deviations(average_parameters_errors):
    parameters_standard_deviations = {}

    trace_filenames = [f'trace{i}' for i in range(1, 11)]
    nr_of_traces = len(trace_filenames)
    for trace_filename in trace_filenames:
        with open(f'{RESULTS_BASE_PATH}/{trace_filename}.json', 'r') as file:
            results = json.load(file)

        for result in results:
            parameters = result['filename']
            averages = average_parameters_errors[parameters]

            if parameters not in parameters_standard_deviations:
                standard_deviations = {
                    'mean_error_std': (result['mean_error'] - averages['mean_error']) ** 2,
                    'rms_error_std': (result['rms_error'] - averages['rms_error']) ** 2,
                    'median_error_std': (result['median_error'] - averages['median_error']) ** 2,
                    'percentile_75th_error_std': (result['percentile_75th_error']
                                                  - averages['percentile_75th_error']) ** 2,
                    'percentile_90th_error_std': (result['percentile_90th_error']
                                                  - averages['percentile_90th_error']) ** 2
                }
            else:
                standard_deviations = parameters_standard_deviations[parameters]

                standard_deviations['mean_error_std'] += (result['mean_error'] - averages['mean_error']) ** 2
                standard_deviations['rms_error_std'] += (result['rms_error'] - averages['rms_error']) ** 2
                standard_deviations['median_error_std'] += (result['median_error'] - averages['median_error']) ** 2
                standard_deviations['percentile_75th_error_std'] += (result['percentile_75th_error']
                                                                     - averages['percentile_75th_error']) ** 2
                standard_deviations['percentile_90th_error_std'] += (result['percentile_90th_error']
                                                                     - averages['percentile_90th_error']) ** 2

            parameters_standard_deviations[parameters] = standard_deviations

    for parameters, standard_deviations in parameters_standard_deviations.items():
        stds = {}
        for metric, std in standard_deviations.items():
            stds[metric] = math.sqrt(std / nr_of_traces)

        parameters_standard_deviations[parameters] = stds

    return parameters_standard_deviations


def plot_parameters(parameter='windowSize', metric='median_error'):
    parameter_labels = {
        'distanceMethod': 'RSSI filtering method',
        'windowSize': 'Window size',
        'distanceModel': 'Distance model',
        'pathLossExponent': 'Path loss exponent',
        'positioningMethod': 'Positioning method',
        'weightExponent': 'Weight exponent',
        'pdfSharpness': 'Probability sharpness'
    }

    parameter_title_labels = {
        'distanceMethod': 'RSSI filtering methods',
        'windowSize': 'window sizes',
        'distanceModel': 'distance models',
        'pathLossExponent': 'path loss exponents',
        'positioningMethod': 'positioning methods',
        'weightExponent': 'weight exponents',
        'pdfSharpness': 'probability sharpness values'
    }

    parameters_errors_average = aggregate_results()

    parameter_errors_tuples = [(split_parameters(filename)[parameter], errors[metric])
                               for filename, errors in parameters_errors_average.items()]

    # Remove occurrences of -1
    parameter_errors_tuples = [x for x in parameter_errors_tuples if x[0] != '-1.0' and x[0] != '-1']

    # Sort list by parameter
    parameter_errors_tuples.sort(key=lambda x: int(x[0]) if x[0].isdigit() else x[0])

    # Group parameters into separated lists of the corresponding errors
    error_groups = []
    parameter_values = []
    for k, g in itertools.groupby(parameter_errors_tuples, lambda x: x[0]):
        error_groups.append(list([error_tuple[1] for error_tuple in g]))
        k = k.replace('_', ' ').capitalize()
        k = k.replace('d los', 'd LOS').replace('nlos', 'NLOS').replace('Path loss', 'Log-distance\npath loss')
        parameter_values.append(k)

    for i, error_group in enumerate(error_groups):
        # Randomly jitter values horizontally to prevent overlap
        jittered_parameter_indices = [np.random.normal(i, 0.06) for _ in range(len(error_group))]
        plt.scatter(jittered_parameter_indices, error_group, alpha=0.12, s=10, color='tab:blue')

    bp = plt.boxplot(error_groups, positions=range(0, len(parameter_values)), labels=parameter_values, notch=True,
                     showfliers=False, boxprops=dict(linewidth=1.5), whiskerprops=dict(linewidth=1.5),
                     medianprops=dict(color='black'))

    for i, parameter_value in enumerate(parameter_values):
        median = np.median(error_groups[i])

        print(parameter_labels[parameter], '-', parameter_value)
        print(f'Median: {median}')
        print(f'95% CI: [{bp["boxes"][i].get_ydata()[2]}, {bp["boxes"][i].get_ydata()[4]}],'
              f' +- {median - bp["boxes"][i].get_ydata()[2]}')
        print()

    plt.xlabel(parameter_labels[parameter], labelpad=10)
    # plt.yticks(np.arange(1.25, 4, 0.25))
    plt.ylabel('Positioning error (m)', labelpad=10)
    plt.title(f'Positioning error for the {parameter_title_labels[parameter]}')
    plt.grid(linewidth=0.5, alpha=0.5, axis='y')

    plt.show()
    # plt.savefig(f'{parameter}.pdf', bbox_inches="tight")
    # plt.clf()


def plot_all_results(metric='median_error'):
    parameters_errors_average = aggregate_results()

    parameter_errors = [errors[metric] for errors in parameters_errors_average.values()]

    jittered_parameter_indices = [np.random.normal(0, 0.06) for _ in range(len(parameter_errors))]
    plt.scatter(jittered_parameter_indices, parameter_errors, alpha=0.3, s=2, color='tab:blue')

    bp = plt.boxplot(parameter_errors, positions=[0], showfliers=False, notch=True,
                     boxprops=dict(linewidth=1.5), whiskerprops=dict(linewidth=1.5),
                     medianprops=dict(color='black'))

    median = np.median(parameter_errors)
    print('All parameter combinations')
    print(f'Median: {median}')
    print(f'95% CI: [{bp["boxes"][0].get_ydata()[2]}, {bp["boxes"][0].get_ydata()[4]}],'
          f' +- {median - bp["boxes"][0].get_ydata()[2]}')

    plt.xticks([], [])
    # plt.yticks(np.arange(1.25, 4, 0.25))
    plt.ylabel('Positioning error (m)', labelpad=10)
    plt.title(f'Positioning error for all parameter combinations')
    plt.grid(linewidth=0.5, alpha=0.5, axis='y')

    plt.show()
    # plt.savefig('all-parameters.pdf', bbox_inches="tight")
    # plt.clf()


def main():
    np.random.seed(0)

    plot_parameters('distanceMethod', metric='median_error')
    plot_parameters('windowSize')
    plot_parameters('distanceModel')
    plot_parameters('pathLossExponent')
    plot_parameters('positioningMethod')
    plot_parameters('weightExponent')
    plot_parameters('pdfSharpness')

    # parameters_errors_average = aggregate_results()
    # stds = calculate_standard_deviations(parameters_errors_average)
    # i = 1
    # for key, values in itertools.islice(parameters_errors_average.items(), 0, 100):
    #     print(f'Rank {i}: {key}')
    #     print(values)
    #     print(stds[key])
    #     i += 1

    plot_all_results()


if __name__ == "__main__":
    main()
