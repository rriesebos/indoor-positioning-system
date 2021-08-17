import os
import re
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt


def calculate_averages(directory, channel=None):
    directory_encoded = os.fsencode(directory)
    files = os.listdir(directory_encoded)
    files.sort(key=lambda f: int(re.sub(r'\D', '', os.fsdecode(f))))

    averages = []
    standard_deviations = []
    for file in files:
        filename = os.fsdecode(file)
        if filename.endswith(".json"):
            df = pd.read_json(f'{directory}/{filename}')

            if channel:
                df = df[df.channel == channel]

            averages.append(df.rssi.mean())
            standard_deviations.append(df.rssi.std(ddof=0))

    return averages, standard_deviations


def plot_measurements(los_directory, nlos_directory, error_bars=True):
    distances = np.arange(0.5, 12.5, 0.5)

    los_averages, los_standard_deviations = calculate_averages(los_directory)
    nlos_averages, nlos_standard_deviations = calculate_averages(nlos_directory)

    plt.figure(figsize=(8, 5))
    if error_bars:
        plt.errorbar(distances, los_averages, marker='o', color='tab:blue', alpha=0.7, label='LOS measurements',
                     yerr=los_standard_deviations, capsize=5)
        plt.errorbar(distances, nlos_averages, marker='s', color='tab:red', alpha=0.7, label='NLOS measurements',
                     yerr=nlos_standard_deviations, capsize=5)
    else:
        plt.plot(distances, los_averages, marker='o', color='tab:blue', alpha=0.7, label='LOS measurements')
        plt.plot(distances, nlos_averages, marker='s', color='tab:red', alpha=0.7, label='NLOS measurements')

    plt.title('RSSI measurements for 0.5 to 12 meters')
    plt.xticks(distances, rotation=45)
    plt.xlabel('Distance (m)')
    plt.ylabel('RSSI (dBm)')
    plt.grid(linewidth=0.5, alpha=0.5)
    plt.legend()
    plt.tight_layout()

    plt.show()


def plot_standard_deviations(los_directory, nlos_directory):
    distances = np.arange(0.5, 12.5, 0.5)

    _, los_standard_deviations = calculate_averages(los_directory)
    _, nlos_standard_deviations = calculate_averages(nlos_directory)

    plt.figure(figsize=(8, 5))
    plt.plot(distances, los_standard_deviations, marker='o', color='tab:blue', alpha=0.7, label='LOS std')
    plt.plot(distances, nlos_standard_deviations, marker='s', color='tab:red', alpha=0.7, label='NLOS std')

    plt.title('RSSI measurements standard deviations for 0.5 to 12 meters')
    plt.xticks(distances, rotation=45)
    plt.xlabel('Distance (m)')
    plt.ylabel('RSSI standard deviation (dBm)')
    plt.grid(linewidth=0.5, alpha=0.5)
    plt.legend()
    plt.tight_layout()

    plt.show()


def plot_path_loss_model(distances, n=2.0, tx_power=-56):
    path_loss = 10 * n * -np.log10(distances) + tx_power
    plt.plot(distances, path_loss, linestyle='dashed', color='tab:green', label='Log-distance path loss model'
                                                                                f' (n = {n:.1f},'
                                                                                f' TX power = {tx_power})')


def plot_fitted_curves(los_directory, nlos_directory, channel=None, save=False, plot_measurements=True, plot_path_loss=False):
    distances = np.arange(0.5, 12.5, 0.5)

    los_averages, _ = calculate_averages(los_directory, channel)
    nlos_averages, _ = calculate_averages(nlos_directory, channel)

    m_los, c_los = np.polyfit(np.log(distances), los_averages, 1)
    print(m_los, c_los)
    los_fitted = np.log(distances) * m_los + c_los
    print(los_fitted)

    m_nlos, c_nlos = np.polyfit(np.log(distances), nlos_averages, 1)
    print(m_nlos, c_nlos)
    nlos_fitted = np.log(distances) * m_nlos + c_nlos

    fitted_averages = np.log(distances) * np.average([m_los, m_nlos]) + np.average([c_los, c_nlos])

    plt.figure(figsize=(8, 5))

    plt.plot(distances, los_fitted, linestyle='dashdot', color='tab:blue', label='Trendline of LOS measurements')
    plt.plot(distances, nlos_fitted, linestyle='dashdot', color='tab:red', label='Trendline of NLOS measurements')

    plt.plot(distances, fitted_averages, linestyle='dashed', color='black', label='Average trendline')

    if plot_path_loss:
        plot_path_loss_model(distances)

    if plot_measurements:
        plt.plot(distances, los_averages, marker='o', color='tab:blue', alpha=0.7, label='LOS measurements')
        plt.plot(distances, nlos_averages, marker='s', color='tab:red', alpha=0.7, label='NLOS measurements')

    if channel:
        plt.title(f'RSSI measurements and their trendlines for 0.5 to 12 meters - channel {channel}')
    else:
        plt.title('RSSI measurements and their trendlines for 0.5 to 12 meters')

    plt.xticks(distances, rotation=45)
    plt.xlabel('Distance (m)')
    plt.ylabel('RSSI (dBm)')
    plt.grid(linewidth=0.5, alpha=0.5)
    plt.legend()
    plt.tight_layout()

    if save:
        plt.savefig('rssi-distance-plot.pdf')
    else:
        plt.show()


def plot_path_loss_models(pl_exponent_range=np.arange(2.0, 3.6, 0.1),
                          tx_power_range=range(-70, -49)):
    distances = np.arange(0.5, 12.5, 0.5)

    default_tx_power = -60
    plt.figure(figsize=(8, 5))
    for n in pl_exponent_range:
        plot_path_loss_model(distances, n=n, tx_power=default_tx_power)

    plt.title('Log-distance path loss model for path loss\n'
              f'exponents (n) from 2.0 to 3.5, and a TX power of {default_tx_power} dBm')
    plt.xticks(np.arange(0.5, 12.5, 0.5), rotation=45)
    plt.yticks(range(-100, -30, 10))
    plt.xlabel('Distance (m)')
    plt.ylabel('RSSI (dBm)')
    plt.grid(linewidth=0.5, alpha=0.5)

    x = distances[-1]
    y = 10 * pl_exponent_range[0] * -np.log10(distances[-1]) + default_tx_power
    plt.annotate(f'n = {pl_exponent_range[0]:.1f}',
                 xy=(x, y), xycoords='data',
                 xytext=(30, 20), textcoords='offset points',
                 arrowprops=dict(arrowstyle="->",
                                 connectionstyle="arc3, rad=.2"))

    y = 10 * pl_exponent_range[-1] * -np.log10(distances[-1]) + default_tx_power
    plt.annotate(f'n = {pl_exponent_range[-1]:.1f}',
                 xy=(x, y), xycoords='data',
                 xytext=(30, 20), textcoords='offset points',
                 arrowprops=dict(arrowstyle="->",
                                 connectionstyle="arc3, rad=-.2"))

    # plt.savefig("path-loss-model-exponent.pdf", bbox_inches="tight")
    plt.show()

    default_pl_exponent = 2.0
    plt.figure(figsize=(9, 5))
    for tx_power in tx_power_range:
        plot_path_loss_model(distances, n=default_pl_exponent, tx_power=tx_power)

    plt.title('Log-distance path loss model for TX powers\n'
              f'from -70 to -50 dBm, and a path loss exponent (n) of {default_pl_exponent}')
    plt.xticks(np.arange(0.5, 12.5, 0.5), rotation=45)
    plt.yticks(range(-100, -30, 10))
    plt.xlabel('Distance (m)')
    plt.ylabel('RSSI (dBm)')
    plt.grid(linewidth=0.5, alpha=0.5)

    y = 10 * default_pl_exponent * -np.log10(distances[-1]) + tx_power_range[0]
    plt.annotate(f'TX power = {tx_power_range[0]}',
                 xy=(x, y), xycoords='data',
                 xytext=(40, 20), textcoords='offset points',
                 arrowprops=dict(arrowstyle="->",
                                 connectionstyle="arc3, rad=.2"))

    y = 10 * default_pl_exponent * -np.log10(distances[-1]) + tx_power_range[-1]
    plt.annotate(f'TX power = {tx_power_range[-1]}',
                 xy=(x, y), xycoords='data',
                 xytext=(40, -10), textcoords='offset points',
                 arrowprops=dict(arrowstyle="->",
                                 connectionstyle="arc3, rad=-.2"))

    # plt.savefig("path-loss-model-tx-power.pdf", bbox_inches="tight")
    plt.show()


def plot_fitted_curves_channel(los_directory, nlos_directory, channel):
    distances = np.arange(0.5, 12.5, 0.5)

    los_averages, _ = calculate_averages(los_directory, channel)
    nlos_averages, _ = calculate_averages(nlos_directory, channel)

    m_los, c_los = np.polyfit(np.log(distances), los_averages, 1)
    print(m_los, c_los)
    los_fitted = np.log(distances) * m_los + c_los

    m_nlos, c_nlos = np.polyfit(np.log(distances), nlos_averages, 1)
    print(m_nlos, c_nlos)
    nlos_fitted = np.log(distances) * m_nlos + c_nlos

    fitted_averages = np.log(distances) * np.average([m_los, m_nlos]) + np.average([c_los, c_nlos])

    colors = {
        37: 'tab:orange',
        38: 'tab:blue',
        39: 'tab:green'
    }

    plt.plot(distances, los_fitted, linestyle='solid', color=colors[channel],
             label=f'LOS trendline for channel {channel}')
    plt.plot(distances, fitted_averages, linestyle='dashed', color=colors[channel],
             label=f'Average trendline for channel {channel}')
    plt.plot(distances, nlos_fitted, linestyle='dashdot', color=colors[channel],
             label=f'NLOS trendline for channel {channel}')

    plt.xticks(distances, rotation=45)
    plt.xlabel('Distance (m)')
    plt.ylabel('RSSI (dBm)')


def plot_channel_comparison(los_channel_directory, nlos_channel_directory):
    plt.figure(figsize=(8, 5))

    for channel in [37, 38, 39]:
        plot_fitted_curves_channel(los_channel_directory, nlos_channel_directory, channel)

    plt.title('Fitted trendlines for 0.5 to 12 meters and different channels')
    plt.grid(linewidth=0.5, alpha=0.5)
    plt.legend()
    plt.tight_layout()

    # plt.show()
    plt.savefig('rssi-distance-trendlines-channels.pdf')


def main():
    # los_directory = 'distance-measurements/line-of-sight'
    # nlos_directory = 'distance-measurements/non-line-of-sight'
    # plot_measurements(los_directory, nlos_directory)
    # plot_standard_deviations(los_directory, nlos_directory)
    # plot_path_loss_models()
    # plot_fitted_curves(los_directory, nlos_directory, save=True, plot_measurements=False, plot_path_loss=True)

    los_channel_directory = 'distance-channel-measurements/line-of-sight'
    nlos_channel_directory = 'distance-channel-measurements/non-line-of-sight'
    # plot_fitted_curves(los_channel_directory, nlos_channel_directory, channel=37, save=False)
    # plot_fitted_curves(los_channel_directory, nlos_channel_directory, channel=38, save=False)
    # plot_fitted_curves(los_channel_directory, nlos_channel_directory, channel=39, save=False)
    plot_channel_comparison(los_channel_directory, nlos_channel_directory)


if __name__ == "__main__":
    main()

