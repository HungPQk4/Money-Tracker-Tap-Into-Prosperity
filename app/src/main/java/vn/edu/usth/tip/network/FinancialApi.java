package vn.edu.usth.tip.network;

import java.util.List;
import java.util.UUID;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import vn.edu.usth.tip.network.requests.FinancialRequests.CreateAccountRequest;
import vn.edu.usth.tip.network.requests.FinancialRequests.CreateBudgetRequest;
import vn.edu.usth.tip.network.requests.FinancialRequests.CreateCategoryRequest;
import vn.edu.usth.tip.network.requests.FinancialRequests.CreateDebtRequest;
import vn.edu.usth.tip.network.requests.FinancialRequests.CreateGoalRequest;
import vn.edu.usth.tip.network.responses.FinancialDtos.AccountDto;
import vn.edu.usth.tip.network.responses.FinancialDtos.BudgetDto;
import vn.edu.usth.tip.network.responses.FinancialDtos.CategoryDto;
import vn.edu.usth.tip.network.responses.FinancialDtos.DebtDto;
import vn.edu.usth.tip.network.responses.FinancialDtos.GoalDto;

public interface FinancialApi {

    @GET("accounts")
    Call<List<AccountDto>> getAllAccounts();

    @POST("accounts")
    Call<AccountDto> createAccount(@Body CreateAccountRequest request);

    @PUT("accounts/{id}")
    Call<AccountDto> updateAccount(@Path("id") UUID id, @Body CreateAccountRequest request);

    @DELETE("accounts/{id}")
    Call<Void> deleteAccount(@Path("id") UUID id);

    @GET("categories")
    Call<List<CategoryDto>> getAllCategories();

    @POST("categories")
    Call<CategoryDto> createCategory(@Body CreateCategoryRequest request);

    @PUT("categories/{id}")
    Call<CategoryDto> updateCategory(@Path("id") UUID id, @Body CreateCategoryRequest request);

    @DELETE("categories/{id}")
    Call<Void> deleteCategory(@Path("id") UUID id);

    @GET("budgets")
    Call<List<BudgetDto>> getAllBudgets();

    @POST("budgets")
    Call<BudgetDto> createBudget(@Body CreateBudgetRequest request);

    @PUT("budgets/{id}")
    Call<BudgetDto> updateBudget(@Path("id") UUID id, @Body CreateBudgetRequest request);

    @DELETE("budgets/{id}")
    Call<Void> deleteBudget(@Path("id") UUID id);

    @GET("goals")
    Call<List<GoalDto>> getAllGoals();

    @POST("goals")
    Call<GoalDto> createGoal(@Body CreateGoalRequest request);

    @PUT("goals/{id}")
    Call<GoalDto> updateGoal(@Path("id") UUID id, @Body CreateGoalRequest request);

    @DELETE("goals/{id}")
    Call<Void> deleteGoal(@Path("id") UUID id);

    @GET("debts")
    Call<List<DebtDto>> getAllDebts();

    @POST("debts")
    Call<DebtDto> createDebt(@Body CreateDebtRequest request);

    @PUT("debts/{id}")
    Call<DebtDto> updateDebt(@Path("id") UUID id, @Body CreateDebtRequest request);

    @DELETE("debts/{id}")
    Call<Void> deleteDebt(@Path("id") UUID id);
}
 
