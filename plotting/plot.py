import os
from pathlib import Path
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
from matplotlib.colors import ListedColormap
import numpy as np


DIRECTORY = '1m-measurements/separate'
CHANNEL_DIRECTORY = 'channel-measurements/oneplus-6'


def plot(file_path, save=False):
    df = pd.read_json(file_path)
    df = df.rename({'system.totimestamp(timeuuid)': 'time'}, axis='columns')
    df.index = pd.to_datetime(df.time)

    n_steps = 30
    smooth_path = df.rolling(n_steps).mean()
    path_deviation = df.rolling(n_steps).std()
    under_line = (smooth_path - path_deviation).rssi
    over_line = (smooth_path + path_deviation).rssi

    file_beacon_address = Path(file_path).stem
    beacon_address = file_beacon_address.replace('-', ':')

    print(beacon_address)
    print(df.mean())
    print(df.std())
    print()

    fig, ax = plt.subplots(figsize=(12, 5))
    plt.plot(smooth_path.rssi, linewidth=2)
    plt.fill_between(path_deviation.index, under_line, over_line, color='b', alpha=.1)
    ax.xaxis.set_major_locator(mdates.SecondLocator(interval=10))
    ax.xaxis.set_major_formatter(mdates.DateFormatter('%H:%M:%S'))
    # ax.xaxis.set_major_locator(mdates.MinuteLocator())
    # ax.xaxis.set_major_formatter(mdates.DateFormatter('%H:%M'))
    plt.title(f'Rolling average and standard deviation of the RSSI over {n_steps} steps for beacon {beacon_address}')
    plt.xticks(rotation=45)
    plt.xlabel('time')
    plt.ylabel('RSSI (dBm)')
    plt.show()

    fig2, ax2 = plt.subplots(figsize=(12, 5))
    df.rssi.plot(ax=ax2, legend=False)
    ax.xaxis.set_major_locator(mdates.SecondLocator(interval=10))
    ax.xaxis.set_major_formatter(mdates.DateFormatter('%H:%M:%S'))
    plt.title(f'RSSI over time for beacon {beacon_address}')
    plt.xticks(rotation=45)
    plt.xlabel('time')
    plt.ylabel('RSSI (dBm)')
    plt.show()

    fig3, ax3 = plt.subplots(figsize=(12, 5))
    ax3.scatter(df.index, df.rssi, s=12)
    ax.xaxis.set_major_locator(mdates.SecondLocator(interval=10))
    ax.xaxis.set_major_formatter(mdates.DateFormatter('%H:%M:%S'))
    plt.title(f'RSSI over time for beacon {beacon_address}')
    plt.xticks(rotation=45)
    plt.xlabel('time')
    plt.ylabel('RSSI (dBm)')
    plt.show()

    fig4, ax4 = plt.subplots()
    df.plot(ax=ax4, kind='kde', x='time', y='rssi', legend=False)
    plt.title(f'RSSI probability density for beacon {beacon_address}')
    plt.xlabel('RSSI (dBm)')
    plt.show()

    if save:
        current_directory = os.getcwd()
        final_directory = os.path.join(current_directory, f'plots/{file_beacon_address}')
        if not os.path.exists(final_directory):
            os.makedirs(final_directory)

        fig.savefig(f'plots/{file_beacon_address}/rssi-rolling-avg-std.pdf')
        fig2.savefig(f'plots/{file_beacon_address}/rssi-line-plot.pdf')
        fig3.savefig(f'plots/{file_beacon_address}/rssi-scatter-plot.pdf')
        fig4.savefig(f'plots/{file_beacon_address}/rssi-pdf.pdf')


def plot_all(save=False):
    directory_encoded = os.fsencode(DIRECTORY)
    for file in os.listdir(directory_encoded):
        filename = os.fsdecode(file)
        if filename.endswith(".json"):
            plot(f'{DIRECTORY}/{filename}', save=save)


def plot_all_channels():
    directory_encoded = os.fsencode(CHANNEL_DIRECTORY)
    for file in os.listdir(directory_encoded):
        filename = os.fsdecode(file)
        if filename.endswith(".json"):
            plot_channels(f'{CHANNEL_DIRECTORY}/{filename}')


def plot_channels(file_path):
    df = pd.read_json(file_path)
    df = df.rename({'system.totimestamp(timeuuid)': 'time'}, axis='columns')
    df.index = pd.to_datetime(df.time)

    file_beacon_address = Path(file_path).stem
    beacon_address = file_beacon_address.replace('-', ':')

    # df.channel = df.channel.shift(periods=1, fill_value=37)

    # Interchannel variance and total variance
    print(beacon_address)
    print(f'Variance of channel 37: {df.rssi[df.channel == 37].var()}')
    print(f'Variance of channel 38: {df.rssi[df.channel == 38].var()}')
    print(f'Variance of channel 39: {df.rssi[df.channel == 39].var()}')
    print(f'Variance of all channels: {df.rssi.var()}')
    print()

    colormap = ListedColormap(['#E69F00', '#56B4E9', '#009E73'])
    colors = [channel - 37 for channel in df.channel]

    fig3, ax3 = plt.subplots(figsize=(10, 5))
    scatter = ax3.scatter(np.arange(0, len(df)), df.rssi, s=12, c=colors, cmap=colormap)
    plt.title(f'RSSI over time with channel information for beacon {beacon_address}')
    plt.xticks(rotation=45)
    plt.xlabel('Measurement')
    plt.ylabel('RSSI (dBm)')
    plt.legend(handles=scatter.legend_elements()[0], labels=[37, 38, 39], title='Channel')
    plt.tight_layout()

    plt.show()
    # plt.savefig('channels.pdf')


def plot_distance(file_path):
    df = pd.read_json(file_path)
    df = df.rename({'system.totimestamp(timeuuid)': 'time'}, axis='columns')
    df.index = pd.to_datetime(df.time)

    fig, ax = plt.subplots(figsize=(12, 5))
    ax.scatter(np.arange(0, len(df)), df.distance, s=12)
    plt.title(f'Distance over time for beacon')
    plt.xticks(rotation=45)
    plt.ylabel('Distance (m)')
    plt.show()


def plot_rssi(file_path):
    df = pd.read_json(file_path)
    df = df.rename({'system.totimestamp(timeuuid)': 'time'}, axis='columns')
    df.index = pd.to_datetime(df.time)

    fig, ax = plt.subplots(figsize=(12, 5))
    ax.scatter(np.arange(0, len(df)), df.rssi, s=12)
    plt.title(f'RSSI over time')
    plt.xticks(rotation=45)
    plt.ylabel('RSSI (dBm)')
    plt.show()


def main():
    # plot_all(save=False)
    # plot(f'{DIRECTORY}/00-CD-FF-0E-5E-B9.json')
    # plot(f'{DIRECTORY}/20-18-FF-00-3F-E4.json')
    # plot_all_channels()

    plot_distance('distance-method/window-length-5/mean.json')
    plot_distance('distance-method/window-length-10/mean.json')

    plot_distance('distance-method/window-length-5/median.json')
    plot_distance('distance-method/window-length-10/median.json')

    plot_distance('distance-method/window-length-5/mode.json')
    plot_distance('distance-method/window-length-10/mode.json')


if __name__ == "__main__":
    main()
