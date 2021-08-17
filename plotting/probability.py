import matplotlib.pyplot as plt
import numpy as np


def plot_probability(distances, probability_sharpness, estimated_distance=2.0):
    probability = 1 / ((distances - estimated_distance) ** 2 + probability_sharpness)
    plt.plot(distances, probability, label=f'{probability_sharpness:.2f}')


def plot_probabilities(distances, probability_sharpnesses, estimated_distance=2.0):
    plt.figure(figsize=(8, 5))
    for c in probability_sharpnesses:
        plot_probability(distances, c)

    plt.grid(linewidth=0.5, alpha=0.5)
    plt.title('Probability density for distances from 0.5 to 12 meters, an estimated\n'
              f'distance of {estimated_distance:.1f} meters and different probability sharpness values (c)')
    plt.xticks(np.arange(0.5, 12.5, 0.5), rotation=45)
    plt.xlabel('Distance (m)')
    plt.ylabel('Probability density')
    legend = plt.legend(title='Probability\nsharpness (c)')
    plt.setp(legend.get_title(), multialignment='center')

    # plt.savefig('probability-sharpnesses.pdf', bbox_inches='tight')
    plt.show()


def plot_weight(distances, weight_exponent):
    weight = 1 / distances ** weight_exponent
    plt.plot(distances, weight, color='tab:blue')


def plot_weights(distances, weight_exponents):
    plt.figure(figsize=(8, 5))
    for g in weight_exponents:
        plot_weight(distances, g)

    plt.grid(linewidth=0.5, alpha=0.5)
    plt.title('Weight for distances from 0.5 to 12 meters, using different weight exponents (g)')
    plt.xticks(np.arange(0.5, 12.5, 0.5), rotation=45)
    plt.xlabel('Distance (m)')
    plt.ylabel('Weight')

    x = distances[0] + 0.1
    y = 1 / x ** weight_exponents[0] - 0.05
    plt.annotate(f'g = {weight_exponents[0]:.1f}',
                 xy=(x, y), xycoords='data',
                 xytext=(-16, -50), textcoords='offset points',
                 arrowprops=dict(arrowstyle="->",
                                 connectionstyle="arc3, rad=-.1"))

    x = distances[-1]
    y = 1 / x ** weight_exponents[0]
    plt.annotate(f'g = {weight_exponents[0]:.1f}',
                 xy=(x, y), xycoords='data',
                 xytext=(30, 20), textcoords='offset points',
                 arrowprops=dict(arrowstyle="->",
                                 connectionstyle="arc3, rad=.2"))

    x = distances[0] + 0.02
    y = 1 / x ** weight_exponents[-1]
    plt.annotate(f'g = {weight_exponents[-1]:.1f}',
                 xy=(x + 0.05, y), xycoords='data',
                 xytext=(30, 10), textcoords='offset points',
                 arrowprops=dict(arrowstyle="->",
                                 connectionstyle="arc3, rad=.15"))

    x = distances[-1]
    y = 1 / x ** weight_exponents[-1]
    plt.annotate(f'g = {weight_exponents[-1]:.1f}',
                 xy=(x + 0.02, y), xycoords='data',
                 xytext=(30, 0), textcoords='offset points',
                 arrowprops=dict(arrowstyle="->",
                                 connectionstyle="arc3, rad=-.2"))

    # plt.savefig('weight-exponents.pdf', bbox_inches='tight')
    plt.show()


def main():
    distances = np.arange(0.5, 12, 0.01)

    probability_sharpnesses = np.arange(0.5, 2.1, 0.25)
    plot_probabilities(distances, probability_sharpnesses)

    weight_exponents = np.arange(0.5, 2.1, 0.25)
    plot_weights(distances, weight_exponents)


if __name__ == '__main__':
    main()
