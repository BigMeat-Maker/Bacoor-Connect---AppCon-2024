// ...existing code...

public class WeatherDash extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather_dash);

        // Example data for testing
        List<DailyForecast> dailyForecasts = new ArrayList<>();
        dailyForecasts.add(new DailyForecast("Monday", 70, 10, 30, 24, 1, 15));
        dailyForecasts.add(new DailyForecast("Tuesday", 65, 5, 32, 25, 2, 10));

        List<HourlyForecast> hourlyForecasts = new ArrayList<>();
        hourlyForecasts.add(new HourlyForecast("12 PM", 60, 0, 30, 32, 10));
        hourlyForecasts.add(new HourlyForecast("1 PM", 55, 0, 31, 33, 12));

        // Load fragments
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.dailyWeatherContainer, WeatherDaily.newInstance(dailyForecasts))
                .replace(R.id.hourlyWeatherContainer, WeatherHourly.newInstance(hourlyForecasts))
                .commit();
    }
}

// ...existing code...
